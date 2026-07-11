/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  --------------------------
 *  This code was previously licensed under
 *
 *  MIT License
 *
 *  Copyright (c) 2018 Gokul Swaminathan
 *  Copyright (c) 2023 Mudlej
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.Context
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.math.min

fun computeHash(bytes: ByteArray): String? {
    return runCatching {
        val digester = MessageDigest.getInstance("MD5")
        digester.update(bytes, 0, min(PDF.HASH_SIZE, bytes.size))
        String.format("%032x", BigInteger(1, digester.digest()))
    }.getOrNull()
}

fun computeHash(file: File): String? {
    return runCatching {
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(PDF.HASH_SIZE)
            var totalRead = 0
            while (totalRead < buffer.size) {
                val amountRead = stream.read(buffer, totalRead, buffer.size - totalRead)
                if (amountRead == -1) break
                totalRead += amountRead
            }
            if (totalRead == 0) return@use null
            val digester = MessageDigest.getInstance("MD5")
            digester.update(buffer, 0, totalRead)
            String.format("%032x", BigInteger(1, digester.digest()))
        }
    }.getOrNull()
}

suspend fun computeHash(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    val cachedBytes = PdfBytesHolder.bytesFor(uri.toString())
    if (cachedBytes != null) {
        return computeHash(cachedBytes)
    }
    return try {
        val digester = MessageDigest.getInstance("MD5")
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            inputStream.use { stream ->
                val buffer = ByteArray(PDF.HASH_SIZE)
                var totalRead = 0
                while (totalRead < buffer.size) {
                    val amountRead = stream.read(buffer, totalRead, buffer.size - totalRead)
                    if (amountRead == -1) break
                    totalRead += amountRead
                }
                if (totalRead == 0) return@withContext null
                digester.update(buffer, 0, totalRead)
            }
        }
        String.format("%032x", BigInteger(1, digester.digest()))
    } catch (e: NoSuchAlgorithmException) {
        Log.e("FileHash", "NoSuchAlgorithmException: computeHash failed!", e)
        null
    } catch (e: IOException) {
        Log.e("FileHash", "IOException: computeHash failed!", e)
        null
    } catch (e: SecurityException) {
        Log.e("FileHash", "SecurityException: computeHash failed!", e)
        null
    } catch (e: Throwable) {
        Log.e("FileHash", "computeHash failed!", e)
        null
    }
}
