package com.chuishui.katago.ai.queue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.PriorityQueue

/**
 * Priority request queue for AI analysis.
 *
 * At most one request is executed at a time; entries are picked strictly by
 * priority (and then FIFO). Analysis entries that became stale while queued
 * (e.g. the human already played several more moves) are dropped instead of
 * wasting an API call.
 */
class AiRequestQueue {

    enum class Priority(val weight: Int) {
        USER_REQUEST(100),
        BLUNDER(90),
        TURNING_POINT(80),
        MISTAKE(60),
        FIGHT(40),
        GOOD_MOVE(20),
    }

    private class Entry<R>(
        val id: Long,
        val priority: Int,
        val isStale: () -> Boolean,
        val block: suspend () -> R,
        val onResult: (Result<R>) -> Unit,
    )

    private val lock = Any()
    @Suppress("UNCHECKED_CAST")
    private val pending = PriorityQueue<Entry<Any>>() { a, b ->
        if (a.priority != b.priority) b.priority - a.priority
        else (a.id - b.id).toInt()
    }
    private val signal = Channel<Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var seq = 0L

    init {
        scope.launch {
            while (isActive) {
                signal.receive()
                drain()
            }
        }
    }

    fun <R> enqueue(
        priority: Priority,
        isStale: () -> Boolean = { false },
        block: suspend () -> R,
        onResult: (Result<R>) -> Unit = {},
    ) {
        @Suppress("UNCHECKED_CAST")
        val entry = Entry<Any>(++seq, priority.weight, isStale, block as suspend () -> Any, onResult as (Result<Any>) -> Unit)
        synchronized(lock) { pending.add(entry) }
        signal.trySend(Unit)
    }

    private suspend fun drain() {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val entry = synchronized(lock) { pending.poll() } ?: break
            if (entry.isStale()) continue
            val result = runCatching { entry.block() }
            entry.onResult(result as Result<Any>)
        }
    }

    fun pendingCount(): Int = synchronized(lock) { pending.size }

    fun clear() = synchronized(lock) { pending.clear() }
}