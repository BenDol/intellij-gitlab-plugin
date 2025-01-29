package com.bendol.intellij.gitlab

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object Utils {
    var debugEnabled: Boolean = false

    private val executor = Executors.newScheduledThreadPool(5)
    private val timers = mutableListOf<ScheduledFuture<*>>()

    fun executeAfterDelay(seconds: Long, runnable: () -> Unit): ScheduledFuture<*> {
        val future = executor.schedule(runnable, seconds, TimeUnit.SECONDS)
        timers.add(future)
        return future
    }

    fun cancelAllTimers() {
        timers.forEach { it.cancel(false) }
        timers.clear()
    }
}
