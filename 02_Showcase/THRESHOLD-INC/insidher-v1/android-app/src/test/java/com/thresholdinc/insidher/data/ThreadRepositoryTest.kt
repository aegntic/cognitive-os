package com.thresholdinc.insidher.data

import com.thresholdinc.insidher.net.CachedMessage
import com.thresholdinc.insidher.net.CachedThread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ThreadRepository cache")
class ThreadRepositoryTest {

    private lateinit var repo: ThreadRepository

    @BeforeEach
    fun setUp() {
        repo = ThreadRepository()
    }

    @Test
    fun putAndListSortedByUpdatedAt() {
        repo.putThreads(
            listOf(
                CachedThread("a", "NEW", "p", "+1", updatedAt = "2024-01-01"),
                CachedThread("b", "CONVERSING", "p", "+2", updatedAt = "2024-06-01"),
            ),
        )
        val list = repo.listThreads()
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(2, repo.threadCount())
    }

    @Test
    fun messagesPerThread() {
        repo.appendMessage("t1", CachedMessage("m1", "t1", "inbound", "hi", "t"))
        repo.appendMessage("t1", CachedMessage("m2", "t1", "outbound", "hey", "t2"))
        assertEquals(2, repo.getMessages("t1").size)
        assertEquals(emptyList<CachedMessage>(), repo.getMessages("missing"))
    }

    @Test
    fun putMessagesReplaces() {
        repo.putMessages(
            "t1",
            listOf(CachedMessage("m1", "t1", "inbound", "a", "t")),
        )
        repo.putMessages(
            "t1",
            listOf(CachedMessage("m9", "t1", "inbound", "b", "t")),
        )
        assertEquals(listOf("m9"), repo.getMessages("t1").map { it.id })
    }

    @Test
    fun clearEmpties() {
        repo.putThread(CachedThread("x", "NEW", "p", "+1"))
        repo.clear()
        assertNull(repo.getThread("x"))
        assertEquals(0, repo.threadCount())
    }
}
