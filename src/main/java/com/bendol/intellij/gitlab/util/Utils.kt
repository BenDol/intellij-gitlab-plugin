package com.bendol.intellij.gitlab.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object Utils {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val timers = mutableListOf<ScheduledFuture<*>>()

    /**
     * Schedules a runnable to be executed after a specified delay using coroutines.
     *
     * @param scope The CoroutineScope to use for launching the coroutine.
     * @param seconds The delay in seconds before executing the runnable.
     * @param dispatcher The CoroutineDispatcher to use for launching the coroutine.
     * @param runnable The task to execute.
     * @return A Job representing the pending task.
     */
    fun executeAfterDelay(
        scope: CoroutineScope,
        seconds: Long,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        runnable: suspend () -> Unit
    ): Job {
        return scope.launch(dispatcher) {
            delay(TimeUnit.SECONDS.toMillis(seconds))
            runnable()
        }
    }

    /**
     * Schedules a runnable to be executed after a specified delay without using coroutines.
     *
     * @param seconds The delay in seconds before executing the runnable.
     * @param runnable The task to execute.
     * @return A ScheduledFuture representing the pending task.
     */
    fun executeAfterDelay(
        seconds: Long,
        runnable: () -> Unit
    ): ScheduledFuture<*> {
        return scheduler.schedule(runnable, seconds, TimeUnit.SECONDS)
    }

    fun cancelAllTimers() {
        timers.forEach { it.cancel(false) }
        timers.clear()
    }
}
