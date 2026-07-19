// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.core.io.UriCanonicalizer
import java.io.File

enum class Availability { AVAILABLE, LOCKED, MISSING }

class AvailabilityProbe(
    private val context: Context,
    private val hasFullAccess: Boolean,
) {

    private val persistedReadGrants: Set<String> by lazy {
        runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun availabilityOf(uri: Uri): Availability {
        return when (uri.scheme) {
            "file" -> {
                val readable = uri.path?.let { File(it).canRead() } == true
                when {
                    readable -> Availability.AVAILABLE
                    hasFullAccess -> Availability.MISSING
                    else -> Availability.LOCKED
                }
            }
            "content" -> {
                when {
                    uri.toString() in persistedReadGrants -> Availability.AVAILABLE
                    UriCanonicalizer.canonicalize(context, uri) != null -> Availability.AVAILABLE
                    uri.authority == MEDIA_AUTHORITY && !hasFullAccess -> Availability.LOCKED
                    else -> Availability.MISSING
                }
            }
            else -> Availability.AVAILABLE
        }
    }

    private companion object {
        const val MEDIA_AUTHORITY = "media"
    }
}
