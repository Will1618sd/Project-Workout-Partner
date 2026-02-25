package com.epfl.esl.workoutapp.wear.navigation

import com.epfl.esl.workoutapp.wear.domain.ActivityType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NavEvents {

    sealed class Event {
        data class StartSessionEvent(val type: ActivityType) : Event()
        data object StopSessionEvent : Event()
        data class SetIndoorStarted(val started: Boolean) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun tryEmit(e: Event) = _events.tryEmit(e)
}