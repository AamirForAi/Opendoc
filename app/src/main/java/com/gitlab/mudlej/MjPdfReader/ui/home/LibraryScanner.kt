// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import android.os.ParcelFileDescriptor
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

class LibraryScanner private constructor(
    private val context: Context,
    private val pdfRepository: PdfRepository,
) {

    data class LibraryIndex(
        val entries: List<ScannedPdfEntry> = emptyList(),
        val scanning: Boolean = false,
        val loaded: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pdfiumCore by lazy { PdfiumCore(context) }
    private val pref by lazy { Preferences(PreferenceManager.getDefaultSharedPreferences(context)) }
    private val indexState = MutableStateFlow(LibraryIndex())
    val index: StateFlow<LibraryIndex> = indexState.asStateFlow()

    private var pipelineJob: Job? = null
    private var quickSyncJob: Job? = null
    private var lastCompletedAt = 0L
    private var observer: ContentObserver? = null
    private var progressVisible = false

    init {
        scope.launch {
            val rows = pdfRepository.findAllScannedPdfs()
            if (rows.isNotEmpty() && pref.getScanMode() == ScanMode.NOT_CONFIGURED) {
                pref.setScanMode(ScanMode.WHOLE_DEVICE)
            }
            indexState.update { state ->
                if (state.loaded) state else state.copy(entries = rows, loaded = true)
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (!PermissionManager.hasFullAccess(context)) {
            return
        }
        if (pipelineJob?.isActive == true) {
            return
        }
        if (!force && System.currentTimeMillis() - lastCompletedAt < STALE_MILLIS) {
            return
        }
        pipelineJob = scope.launch {
            quickSyncJob?.cancel()
            runCatching {
                val mode = pref.getScanMode()
                val current = LinkedHashMap<String, ScannedPdfEntry>()
                pdfRepository.findAllScannedPdfs().forEach { current[it.path] = it }
                progressVisible = current.isEmpty()
                publish(current, scanning = progressVisible)

                val seenPaths = HashSet<String>()
                mediaStorePass(current, seenPaths)
                publish(current, scanning = progressVisible)
                if (mode != ScanMode.NOT_CONFIGURED) {
                    walkPass(current, seenPaths, walkRoots(mode))
                }
                pruneMissing(current, seenPaths)
                publish(current, scanning = false)
                lastCompletedAt = System.currentTimeMillis()

                hashBackfill(current, mode)
            }.onFailure {
                indexState.update { state -> state.copy(scanning = false) }
            }
        }
    }

    fun startObserving() {
        if (observer != null) {
            return
        }
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onMediaStoreChanged()
            }
        }.also {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri(EXTERNAL_VOLUME), true, it
            )
        }
        onMediaStoreChanged()
    }

    fun stopObserving() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    suspend fun findPathByHash(hash: String): String? {
        return withTimeoutOrNull(RELINK_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                pdfRepository.findScannedPdfsByHash(hash)
                    .firstOrNull { File(it.path).canRead() }
                    ?.path
            }
        }
    }

    fun onFileRemoved(path: String) {
        scope.launch {
            pdfRepository.pruneScannedPdfs(listOf(path))
            indexState.update { state ->
                state.copy(entries = state.entries.filter { it.path != path })
            }
        }
    }

    fun onFileRenamed(oldPath: String, newPath: String) {
        scope.launch {
            pdfRepository.updateScannedPdfPath(oldPath, newPath)
            indexState.update { state ->
                state.copy(entries = state.entries.map { entry ->
                    if (entry.path == oldPath) entry.copy(path = newPath) else entry
                })
            }
        }
    }

    fun onHashComputed(path: String, hash: String) {
        scope.launch {
            val entry = indexState.value.entries.find { it.path == path } ?: return@launch
            if (entry.hash == hash) {
                return@launch
            }
            val withHash = entry.copy(hash = hash)
            pdfRepository.upsertScannedPdfs(listOf(withHash))
            indexState.update { state ->
                state.copy(entries = state.entries.map {
                    if (it.path == path) withHash else it
                })
            }
        }
    }

    private fun onMediaStoreChanged() {
        quickSyncJob?.cancel()
        quickSyncJob = scope.launch {
            delay(OBSERVER_DEBOUNCE_MILLIS)
            if (pipelineJob?.isActive == true || !PermissionManager.hasFullAccess(context)) {
                return@launch
            }
            runCatching {
                val current = LinkedHashMap<String, ScannedPdfEntry>()
                pdfRepository.findAllScannedPdfs().forEach { current[it.path] = it }
                val seenPaths = HashSet<String>()
                mediaStorePass(current, seenPaths)
                pruneMissing(current, seenPaths)
                publish(current, scanning = false)
                hashBackfill(current, pref.getScanMode())
            }
        }
    }

    fun libraryEntries(): List<ScannedPdfEntry> {
        val roots = ScanScope.displayRoots(pref.getScanMode(), pref.getScanLocations())
        if (roots == null) {
            return indexState.value.entries
        }
        return indexState.value.entries.filter { ScanScope.contains(roots, it.path) }
    }

    private fun walkRoots(mode: ScanMode): List<File> {
        return if (mode == ScanMode.SELECTED_LOCATIONS) {
            ScanScope.normalize(pref.getScanLocations()).map(::File)
        } else {
            listOf(File(StorageManager.ROOT_DIR))
        }
    }

    private suspend fun mediaStorePass(
        current: LinkedHashMap<String, ScannedPdfEntry>,
        seenPaths: MutableSet<String>,
    ) {
        val upserts = mutableListOf<ScannedPdfEntry>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
                arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
                "${MediaStore.MediaColumns.MIME_TYPE} = ? OR ${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf(PDF.FILE_TYPE, "%.$PDF_SUFFIX"),
                null,
            )?.use { cursor ->
                val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (pathIndex < 0 || sizeIndex < 0 || modifiedIndex < 0) {
                    return@use
                }
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIndex) ?: continue
                    if (!path.endsWith(".$PDF_SUFFIX", ignoreCase = true)) {
                        continue
                    }
                    mergeSighting(
                        current,
                        upserts,
                        path,
                        cursor.getLong(sizeIndex),
                        cursor.getLong(modifiedIndex) * 1000,
                    )
                    seenPaths.add(path)
                }
            }
        }
        flushUpserts(upserts)
    }

    private suspend fun walkPass(
        current: LinkedHashMap<String, ScannedPdfEntry>,
        seenPaths: MutableSet<String>,
        roots: List<File>,
    ) {
        val upserts = mutableListOf<ScannedPdfEntry>()
        var lastEmitAt = System.currentTimeMillis()

        StorageManager().readAllFiles(roots)
            .filter { file ->
                file.isFile && file.extension.equals(PDF_SUFFIX, ignoreCase = true)
            }
            .forEach { file ->
                val path = file.absolutePath
                mergeSighting(current, upserts, path, file.length(), file.lastModified())
                seenPaths.add(path)

                val now = System.currentTimeMillis()
                if (upserts.size >= EMIT_BATCH_SIZE || now - lastEmitAt >= EMIT_INTERVAL_MILLIS) {
                    flushUpserts(upserts)
                    publish(current, scanning = progressVisible)
                    lastEmitAt = now
                }
            }
        flushUpserts(upserts)
    }

    private fun mergeSighting(
        current: LinkedHashMap<String, ScannedPdfEntry>,
        upserts: MutableList<ScannedPdfEntry>,
        path: String,
        size: Long,
        lastModified: Long,
    ) {
        val existing = current[path]
        if (existing != null
            && existing.size == size
            && existing.lastModified / 1000 == lastModified / 1000
        ) {
            return
        }
        val entry = ScannedPdfEntry(path, size, lastModified, hash = null)
        current[path] = entry
        upserts.add(entry)
    }

    private suspend fun pruneMissing(
        current: LinkedHashMap<String, ScannedPdfEntry>,
        seenPaths: Set<String>,
    ) {
        val gone = current.keys.filter { it !in seenPaths && !File(it).exists() }
        if (gone.isEmpty()) {
            return
        }
        gone.forEach { current.remove(it) }
        gone.chunked(SQL_CHUNK_SIZE).forEach { pdfRepository.pruneScannedPdfs(it) }
    }

    private suspend fun hashBackfill(current: LinkedHashMap<String, ScannedPdfEntry>, mode: ScanMode) {
        val hashScope = ScanScope.displayRoots(mode, pref.getScanLocations())
        val pending = current.values.filter {
            val stored = it.hash
            (stored == null || DocumentIdentity.isLegacy(stored)) && ScanScope.contains(hashScope, it.path)
        }
        if (pending.isEmpty()) {
            return
        }
        val updated = mutableListOf<ScannedPdfEntry>()
        for (entry in pending) {
            val hash = DocumentIdentity.of(File(entry.path))?.identity
            if (hash != null) {
                val withHash = entry.copy(hash = hash, pageCount = readPageCount(entry.path))
                current[entry.path] = withHash
                updated.add(withHash)
            }
            if (updated.size >= HASH_BATCH_SIZE) {
                flushUpserts(updated)
                publish(current, scanning = false)
            }
            yield()
        }
        flushUpserts(updated)
        publish(current, scanning = false)
    }

    private fun readPageCount(path: String): Int {
        return runCatching {
            val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val document = try {
                pdfiumCore.newDocument(fd)
            } catch (throwable: Throwable) {
                runCatching { fd.close() }
                return 0
            }
            try {
                pdfiumCore.getPageCount(document)
            } finally {
                pdfiumCore.closeDocument(document)
            }
        }.getOrDefault(0)
    }

    private suspend fun flushUpserts(upserts: MutableList<ScannedPdfEntry>) {
        if (upserts.isEmpty()) {
            return
        }
        upserts.chunked(SQL_CHUNK_SIZE).forEach { pdfRepository.upsertScannedPdfs(it) }
        upserts.clear()
    }

    private fun publish(current: LinkedHashMap<String, ScannedPdfEntry>, scanning: Boolean) {
        val snapshot = current.values.toList()
        indexState.update { it.copy(entries = snapshot, scanning = scanning, loaded = true) }
    }

    companion object {
        private const val RELINK_TIMEOUT_MS = 2_000L
        @Volatile
        private var INSTANCE: LibraryScanner? = null

        private const val EXTERNAL_VOLUME = "external"
        private const val PDF_SUFFIX = StorageManager.PDF_EXTENSION
        private const val STALE_MILLIS = 5L * 60 * 1000
        private const val OBSERVER_DEBOUNCE_MILLIS = 2000L
        private const val EMIT_BATCH_SIZE = 50
        private const val EMIT_INTERVAL_MILLIS = 300L
        private const val HASH_BATCH_SIZE = 25
        private const val SQL_CHUNK_SIZE = 500

        fun getInstance(context: Context): LibraryScanner {
            INSTANCE?.let { return it }
            synchronized(this) {
                INSTANCE?.let { return it }
                val appContext = context.applicationContext
                val created = LibraryScanner(
                    appContext,
                    PdfRepository(AppDatabase.getInstance(appContext)),
                )
                INSTANCE = created
                return created
            }
        }
    }
}
