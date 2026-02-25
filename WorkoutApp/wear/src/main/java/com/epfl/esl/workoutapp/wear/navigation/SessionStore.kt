package com.epfl.esl.workoutapp.wear.navigation

import android.util.Log
import com.epfl.esl.workoutapp.wear.domain.ActivityType
import com.epfl.esl.workoutapp.wear.ui.common.WearSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionStore {
    private val _type = MutableStateFlow<ActivityType?>(null)
    val type = _type.asStateFlow()

    fun set(t: ActivityType) { _type.value = t }

    private val _indoorStarted = MutableStateFlow(false)
    val indoorStarted: StateFlow<Boolean> = _indoorStarted.asStateFlow()
    fun setIndoorStarted(started: Boolean) { _indoorStarted.value = started }

    private val _outdoorStarted = MutableStateFlow(false)
    val outdoorStarted: StateFlow<Boolean> = _outdoorStarted
    fun setOutdoorStarted(v: Boolean) { _outdoorStarted.value = v }

    val summary = MutableStateFlow<WearSummary?>(null)
    fun clear() {
        _type.value = null
        _indoorStarted.value = false
        _outdoorStarted.value = false
    }
}