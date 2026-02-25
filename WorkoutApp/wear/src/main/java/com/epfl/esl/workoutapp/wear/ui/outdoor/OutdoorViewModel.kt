package com.epfl.esl.workoutapp.wear.ui.outdoor

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.workoutapp.wear.data.repository.OutdoorMetricsRepository
import com.epfl.esl.workoutapp.wear.data.wearable.WearComms
import com.epfl.esl.workoutapp.wear.domain.OutdoorActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Pool
import com.epfl.esl.workoutapp.wear.data.wearable.Formatters

data class OutdoorUiState(
    val activity: OutdoorActivity = OutdoorActivity.RUNNING,
    val title: String = OutdoorActivity.RUNNING.name,
    val icon: ImageVector = Icons.Filled.DirectionsRun,
    val started: Boolean = false,
    val paused: Boolean = false,
    val timeText: String = "--:--",
    val speedText: String = "-- km/h",
    val distanceText: String = "--",
)
class OutdoorViewModel(
    private val repo: OutdoorMetricsRepository,
    private val comms: WearComms,
) : ViewModel() {

    private val _ui = MutableStateFlow(OutdoorUiState())
    val ui: StateFlow<OutdoorUiState> = _ui


    init {
        repo.start()

        viewModelScope.launch {
            repo.metrics.collect { m ->

                _ui.value = _ui.value.copy(
                    activity = m.activity,
                    title = m.activity.name,
                    icon = iconFor(m.activity),

                    started = m.started,
                    paused = m.paused,

                    timeText = Formatters.timeMmSs(m.timeSec),
                    speedText = Formatters.speedKphText(m.speedKph),
                    distanceText = Formatters.distanceText(m.distanceM),
                )
            }
        }
    }

    fun setActivity(activity: OutdoorActivity) {
        _ui.value = _ui.value.copy(
            activity = activity,
            title = activity.name,
            icon = iconFor(activity)
        )
    }

    fun startSession() {
        _ui.value = _ui.value.copy(started = true, paused = false)
        comms.cmdStartOutdoor(_ui.value.activity)
    }

    fun togglePause() {
        val nowPaused = !_ui.value.paused
        _ui.value = _ui.value.copy(paused = nowPaused)

        if (nowPaused) comms.cmdPauseOutdoor() else comms.cmdResumeOutdoor()
    }

    fun stopSession() {
        _ui.value = _ui.value.copy(started = false, paused = false)
        comms.cmdStopOutdoor(_ui.value.activity)
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
    private fun iconFor(activity: OutdoorActivity) = when (activity) {
        OutdoorActivity.RUNNING  -> Icons.Filled.DirectionsRun
        OutdoorActivity.CYCLING  -> Icons.Filled.DirectionsBike
        OutdoorActivity.SWIMMING -> Icons.Filled.Pool
    }
}

/* ---------- formatting helpers ---------- */

private fun formatTime(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    val mm = s / 60
    val ss = s % 60
    return String.format(Locale.US, "%02d:%02d", mm, ss)
}

private fun formatSpeed(speedKph: Float): String {
    val kmh = speedKph.coerceAtLeast(0f)
    return String.format(Locale.US, "%.1f km/h", kmh)
}

private fun formatDistance(distanceM: Float): String {
    val m = distanceM.coerceAtLeast(0f)
    return if (m >= 1000f) {
        String.format(Locale.US, "%.2f km", m / 1000f)
    } else {
        String.format(Locale.US, "%.0f m", m)
    }
}