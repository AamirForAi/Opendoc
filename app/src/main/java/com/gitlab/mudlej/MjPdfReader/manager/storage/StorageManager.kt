package com.gitlab.mudlej.MjPdfReader.manager.storage

import android.os.Environment
import java.io.File

class StorageManager {

    fun readAllFiles(): FileTreeWalk {
        return File(ROOT_DIR).walk()
            .onEnter { file ->                        // before entering this dir check if
                !file.isHidden                             // it is not hidden
                && file != ANDROID_DIR                     // it is not Android directory
                && file != DATA_DIR                        // it is not data directory
                && !File(file, ".nomedia").exists()   // there is no .nomedia file inside
            }
    }

    companion object {

        val ROOT_DIR = Environment.getExternalStorageDirectory().absolutePath
        private val ANDROID_DIR = File("$ROOT_DIR/Android")
        private val DATA_DIR = File("$ROOT_DIR/data")

        const val PDF_EXTENSION = "pdf"
    }
}
