// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object PersistedGrantKeeper {

    private const val CAP_PRE_R = 128
    private const val CAP_R_AND_UP = 512
    private const val CAP_HEADROOM = 8

    fun takeReadGrant(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") {
            return false
        }
        val resolver = context.contentResolver
        runCatching {
            val cap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CAP_R_AND_UP else CAP_PRE_R
            val persisted = resolver.persistedUriPermissions
            val excess = persisted.size - (cap - CAP_HEADROOM)
            if (excess >= 0) {
                persisted
                    .filter { !it.isWritePermission }
                    .sortedBy { it.persistedTime }
                    .take(excess + 1)
                    .forEach { permission ->
                        runCatching {
                            resolver.releasePersistableUriPermission(
                                permission.uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    }
            }
        }
        return runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
    }
}
