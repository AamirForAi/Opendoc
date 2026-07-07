package com.gitlab.mudlej.MjPdfReader.data.annotation

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class AnnotationJournal(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "annotation-journal")
    }

    fun append(uri: Uri, edit: AnnotationEdit) {
        executor.execute {
            runCatching {
                FileOutputStream(fileFor(uri), true).use { stream ->
                    stream.write((edit.toJsonLine() + "\n").toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
            }.onFailure { error ->
                Log.e(TAG, "append: failed to journal annotation edit", error)
            }
        }
    }

    fun readAll(uri: Uri): List<AnnotationEdit> = onJournalThread {
        val file = fileFor(uri)
        if (!file.isFile) {
            return@onJournalThread emptyList()
        }
        runCatching { file.readLines() }
            .getOrDefault(emptyList())
            .mapNotNull(AnnotationEdit::fromJsonLine)
    }

    fun hasRecords(uri: Uri): Boolean = onJournalThread {
        fileFor(uri).length() > 0
    }

    fun delete(uri: Uri) {
        executor.execute { fileFor(uri).delete() }
    }

    private fun <T> onJournalThread(action: () -> T): T {
        return executor.submit(Callable(action)).get()
    }

    private fun fileFor(uri: Uri): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${SourceKey.of(uri)}.journal")
    }

    private companion object {
        const val TAG = "AnnotationJournal"
    }
}
