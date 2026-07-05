package com.gitlab.mudlej.MjPdfReader.ui.search

object SearchSessionCache {
    private const val MAX_SESSIONS = 5
    private const val NO_POSITION = -1

    data class Key(val fileHash: String, val query: String)

    data class Hit(
        val pageNumber: Int,
        val originalIndex: Int,
        val resultIndex: Int,
        val expanded: Boolean = false,
    )

    data class Session(
        val key: Key,
        val hits: List<Hit>,
        val listPosition: Int = NO_POSITION,
        val listOffsetPx: Int = 0,
        val nestedQuery: String? = null,
    )

    private val sessions = object : LinkedHashMap<Key, Session>(MAX_SESSIONS + 1, 0.75F, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Session>?): Boolean {
            return size > MAX_SESSIONS
        }
    }

    @Synchronized
    fun get(fileHash: String?, query: String): Session? {
        val key = key(fileHash, query) ?: return null
        return sessions[key]
    }

    @Synchronized
    fun put(fileHash: String?, query: String, hits: List<Hit>) {
        val key = key(fileHash, query) ?: return
        sessions[key] = Session(key, hits.toList())
    }

    @Synchronized
    fun updateUiState(
        fileHash: String?,
        query: String,
        listPosition: Int,
        listOffsetPx: Int,
        nestedQuery: String?,
    ) {
        val key = key(fileHash, query) ?: return
        val session = sessions[key] ?: return
        sessions[key] = session.copy(
            listPosition = listPosition,
            listOffsetPx = listOffsetPx,
            nestedQuery = nestedQuery?.takeIf { it.isNotBlank() },
        )
    }

    @Synchronized
    fun setExpanded(fileHash: String?, query: String, resultIndex: Int, expanded: Boolean) {
        val key = key(fileHash, query) ?: return
        val session = sessions[key] ?: return
        sessions[key] = session.copy(
            hits = session.hits.map { hit ->
                if (hit.resultIndex == resultIndex) hit.copy(expanded = expanded) else hit
            },
        )
    }

    private fun key(fileHash: String?, query: String): Key? {
        val hash = fileHash?.takeIf { it.isNotBlank() } ?: return null
        val trimmedQuery = query.trim().takeIf { it.isNotBlank() } ?: return null
        return Key(hash, trimmedQuery)
    }
}
