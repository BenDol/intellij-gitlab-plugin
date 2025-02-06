package com.bendol.intellij.gitlab

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object EventBus {
    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    suspend fun publish(event: Event) {
        _events.emit(event)
    }

    data class Event(val name: String, val projectId: String, val data: Any? = null)
}
