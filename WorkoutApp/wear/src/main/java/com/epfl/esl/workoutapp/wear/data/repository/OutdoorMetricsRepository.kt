package com.epfl.esl.workoutapp.wear.data.repository

import android.content.Context
import com.epfl.esl.workoutapp.wear.data.wearable.OutdoorDataKeys
import com.epfl.esl.workoutapp.wear.domain.OutdoorActivity
import com.epfl.esl.workoutapp.wear.domain.WearPaths
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


data class OutdoorMetrics(
    val activity: OutdoorActivity = OutdoorActivity.RUNNING,
    val timeSec: Int = 0,
    val distanceM: Int = 0,     // meters
    val speedKph: Float = 0f,   // kph
    val started: Boolean = false,
    val paused: Boolean = false,
)
/**
 * Mobile -> Wear repository.
 * Listens for /outdoor_metrics DataItem updates and exposes latest metrics.
 */
class OutdoorMetricsRepository(
    private val dataClient: DataClient
) : DataClient.OnDataChangedListener {

    private val _metrics = MutableStateFlow(OutdoorMetrics())
    val metrics: StateFlow<OutdoorMetrics> = _metrics

    private var listening = false

    fun start() {
        if (listening) return
        listening = true
        dataClient.addListener(this)
    }

    fun stop() {
        if (!listening) return
        listening = false
        dataClient.removeListener(this)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearPaths.OUTDOOR_METRICS) return@forEach

            val map = DataMapItem.fromDataItem(event.dataItem).dataMap

            val activityStr = map.getString(OutdoorDataKeys.ACTIVITY)
                ?: OutdoorActivity.RUNNING.name
            val activity = runCatching { OutdoorActivity.valueOf(activityStr) }
                .getOrDefault(OutdoorActivity.RUNNING)

            _metrics.value = OutdoorMetrics(
                activity = activity,
                timeSec = map.getInt(OutdoorDataKeys.TIME_SEC),
                distanceM = map.getInt(OutdoorDataKeys.DISTANCE_M),
                speedKph = map.getFloat(OutdoorDataKeys.SPEED_KPH),
                started = map.getBoolean(OutdoorDataKeys.STARTED),
                paused = map.getBoolean(OutdoorDataKeys.PAUSED),
            )
        }
    }
}