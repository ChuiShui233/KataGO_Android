package com.chuishui.katago

import com.chuishui.katago.ai.queue.AiRequestQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestQueueTest {

    @Test
    fun highPriorityRunsFirst() = runBlocking {
        val queue = AiRequestQueue()
        val order = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        // First task grabs the worker and blocks so later entries pile up.
        queue.enqueue(AiRequestQueue.Priority.MISTAKE, block = {
            order.add("t1-start")
            release.await()
            order.add("t1-end")
        })
        delay(200) // ensure t1 owns the worker
        queue.enqueue(AiRequestQueue.Priority.MISTAKE, block = { order.add("low") })
        queue.enqueue(AiRequestQueue.Priority.BLUNDER, block = { order.add("high") })
        delay(200) // both now pending
        release.complete(Unit)
        delay(400) // let the backlog drain
        // Of the two queued entries, BLUNDER (90) must run before MISTAKE (60).
        assertEquals(listOf("t1-start", "t1-end", "high", "low"), order)
    }

    @Test
    fun staleEntriesAreSkipped() = runBlocking {
        val queue = AiRequestQueue()
        val order = mutableListOf<String>()
        queue.enqueue(AiRequestQueue.Priority.MISTAKE, isStale = { true }, block = {
            order.add("stale")
        })
        queue.enqueue(AiRequestQueue.Priority.BLUNDER, block = {
            order.add("fresh")
        })
        delay(400)
        assertEquals(listOf("fresh"), order)
    }

    @Test
    fun onResultReceivesExceptionOnFailure() = runBlocking {
        val queue = AiRequestQueue()
        val deferred = CompletableDeferred<Result<String>>()
        queue.enqueue(
            AiRequestQueue.Priority.USER_REQUEST,
            block = { throw IllegalStateException("boom") },
            onResult = { deferred.complete(it) },
        )
        val result = deferred.await()
        assertTrue(result.isFailure)
    }

    @Test
    fun pendingCountReflectsEnqueued() = runBlocking {
        val queue = AiRequestQueue()
        val gate = CompletableDeferred<Unit>()
        queue.enqueue(AiRequestQueue.Priority.MISTAKE, block = { gate.await() })
        queue.enqueue(AiRequestQueue.Priority.BLUNDER, block = { gate.await() })
        delay(200)
        assertEquals(1, queue.pendingCount()) // one grabs the worker, one waits
        gate.complete(Unit)
        delay(200)
        assertEquals(0, queue.pendingCount())
    }
}