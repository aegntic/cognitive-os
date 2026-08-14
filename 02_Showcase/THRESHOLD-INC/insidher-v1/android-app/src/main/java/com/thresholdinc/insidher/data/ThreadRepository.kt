package com.thresholdinc.insidher.data

import com.thresholdinc.insidher.net.CachedMessage
import com.thresholdinc.insidher.net.CachedThread
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory local cache for threads + messages.
 * ponytail: Room/SQLDelight when offline durability matters.
 */
class ThreadRepository {

    private val threads = ConcurrentHashMap<String, CachedThread>()
    private val messages = ConcurrentHashMap<String, MutableList<CachedMessage>>()

    fun putThreads(list: List<CachedThread>) {
        list.forEach { threads[it.id] = it }
    }

    fun putThread(thread: CachedThread) {
        threads[thread.id] = thread
    }

    fun getThread(id: String): CachedThread? = threads[id]

    fun listThreads(): List<CachedThread> =
        threads.values.sortedByDescending { it.updatedAt }

    fun putMessages(threadId: String, list: List<CachedMessage>) {
        messages[threadId] = list.toMutableList()
    }

    fun appendMessage(threadId: String, message: CachedMessage) {
        messages.getOrPut(threadId) { mutableListOf() }.add(message)
    }

    fun getMessages(threadId: String): List<CachedMessage> =
        messages[threadId]?.toList().orEmpty()

    fun clear() {
        threads.clear()
        messages.clear()
    }

    fun threadCount(): Int = threads.size
}
