// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager as AndroidStorageManager
import androidx.core.content.ContextCompat
import java.io.File

class StorageManager {

    fun readAllFiles(): Sequence<File> = readAllFiles(listOf(File(ROOT_DIR)))

    fun readAllFiles(roots: List<File>): Sequence<File> {
        return roots.asSequence().flatMap { root ->
            root.walk().onEnter { file ->
                !file.isHidden
                && file != ANDROID_DIR
                && file != DATA_DIR
                && !File(file, ".nomedia").exists()
            }
        }
    }

    fun volumeRoots(context: Context): List<File> {
        val roots = LinkedHashMap<String, File>()
        val primary = Environment.getExternalStorageDirectory()
        roots[primary.absolutePath] = primary

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(AndroidStorageManager::class.java)
                ?.storageVolumes
                ?.forEach { volume ->
                    val directory = volume.directory ?: return@forEach
                    if (volume.state == Environment.MEDIA_MOUNTED
                        || volume.state == Environment.MEDIA_MOUNTED_READ_ONLY
                    ) {
                        roots.putIfAbsent(directory.absolutePath, directory)
                    }
                }
        } else {
            ContextCompat.getExternalFilesDirs(context, null)
                .filterNotNull()
                .forEach { filesDir ->
                    val volumeRoot = filesDir.parentFile?.parentFile?.parentFile?.parentFile
                        ?: return@forEach
                    roots.putIfAbsent(volumeRoot.absolutePath, volumeRoot)
                }
        }

        return roots.values.filter { it.isDirectory && it.canRead() }
    }

    companion object {

        val ROOT_DIR = Environment.getExternalStorageDirectory().absolutePath
        private val ANDROID_DIR = File("$ROOT_DIR/Android")
        private val DATA_DIR = File("$ROOT_DIR/data")

        const val PDF_EXTENSION = "pdf"
    }
}
