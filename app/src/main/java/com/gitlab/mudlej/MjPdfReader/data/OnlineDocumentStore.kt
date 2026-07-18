// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object OnlineDocumentStore {

    fun fileFor(context: Context, uri: String?): File? {
        if (uri == null) {
            return null
        }
        val name = fileNameFor(uri) ?: return null
        for (directory in listOf(normalDirectory(context), incognitoDirectory(context))) {
            val file = File(directory, name)
            if (file.isFile && file.length() > 0) {
                file.setLastModified(System.currentTimeMillis())
                return file
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun write(
        context: Context,
        uri: String,
        incognito: Boolean,
        input: InputStream,
        onBytesWritten: ((Long) -> Unit)? = null,
    ): File? {
        val directory = if (incognito) incognitoDirectory(context) else normalDirectory(context)
        directory.mkdirs()
        pruneOldFiles(directory)
        val name = fileNameFor(uri) ?: return null
        val target = File(directory, name)
        val part = File(directory, "$name.part")
        try {
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        break
                    }
                    output.write(buffer, 0, read)
                    total += read
                    onBytesWritten?.invoke(total)
                }
            }
        } catch (e: IOException) {
            part.delete()
            throw e
        }
        if (part.length() <= 0L || !part.renameTo(target)) {
            part.delete()
            return null
        }
        return target
    }

    fun sweepIncognito(context: Context) {
        incognitoDirectory(context).listFiles()?.forEach { it.delete() }
    }

    private fun pruneOldFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun fileNameFor(uri: String): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
            digest.joinToString("") { "%02x".format(it) } + ".pdf"
        }.getOrNull()
    }

    private fun normalDirectory(context: Context): File = File(context.cacheDir, NORMAL_DIRECTORY)

    private fun incognitoDirectory(context: Context): File = File(context.cacheDir, INCOGNITO_DIRECTORY)

    private const val NORMAL_DIRECTORY = "online-pdf"
    private const val INCOGNITO_DIRECTORY = "online-pdf-incognito"
    private const val COPY_BUFFER_SIZE = 8 * 1024
    private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
}
