package com.epfl.esl.workoutapp.wear.ui.indoor

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.workoutapp.wear.data.wearable.WearComms
import com.epfl.esl.workoutapp.wear.domain.IndoorStatePacket
import com.epfl.esl.workoutapp.wear.domain.IndoorWorkout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.epfl.esl.workoutapp.wear.data.sensors.SensorHub
import com.epfl.esl.workoutapp.wear.sensor.ImuSample
import com.epfl.esl.workoutapp.wear.sensor.RepDetector
import com.epfl.esl.workoutapp.wear.sensor.RepDetectorFactory
import com.epfl.esl.workoutapp.wear.ui.home.HomeUiState
import com.google.android.gms.wearable.Wearable
//import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine


data class IndoorUiState(
    val workout: IndoorWorkout = IndoorWorkout.DEADLIFT,
    val heartRate: Int = 0,
    val title: String = "DEADLIFT",
    val icon: ImageVector = Icons.Filled.FitnessCenter,
    val started: Boolean = false,
    val isPaused: Boolean = false,
    val weightKg: Int = 60,
    val reps: Int = 0,
    val set: Int = 1,
    val isSaving: Boolean = false

)

class IndoorViewModel(
    private val comms: WearComms,
    private val sensorHub: SensorHub,
//    private val applicationContext: Context

) : ViewModel() {

    private val _ui = MutableStateFlow(IndoorUiState())
    val ui: StateFlow<IndoorUiState> = _ui

    private val _recordedData = mutableListOf<ImuSample>()
    private var txJob: Job? = null


    private var repDetector: RepDetector? = null

    private val _reps = MutableStateFlow(0)
    val reps: StateFlow<Int> = _reps

    private var repJob: Job? = null

    init {
        // 2. Automatically feed sensor data into your recording logic
//        viewModelScope.launch {
////            sensorHub.imu.collect { sample ->
////                onSensorDataReceived(sample)
////            }
//            sensorHub.heartRate.collect { hr ->
//                _ui.value = _ui.value.copy(heartRate = hr)
//            }
//        }
    }

    fun setWorkout(workout: IndoorWorkout) {
        val icon = when (workout) {
            IndoorWorkout.DEADLIFT -> Icons.Filled.Whatshot
            IndoorWorkout.BENCH -> Icons.Filled.FitnessCenter
            IndoorWorkout.SQUAT -> Icons.Filled.AccessibilityNew
        }
        _ui.value = _ui.value.copy(
            workout = workout,
            title = workout.name,
            icon = icon
        )
        // Optional: send immediate state snapshot when selecting workout
        sendStateOnce()
    }

    fun startSession() {
        sensorHub.start()
        _ui.value = _ui.value.copy(started = true, isPaused = false)
        sendStateOnce()          // immediate sync
        startThrottledSync()     // continuous sync
        Log.w("IndoorViewModel", "Start session")
        startRepCounting()
    }

    fun togglePause() {
        val nowPaused = !_ui.value.isPaused
        _ui.value = _ui.value.copy(isPaused = nowPaused)

        // immediate command (mobile stops timer, wear disables algo)
        if (nowPaused) comms.cmdPauseIndoor() else comms.cmdResumeIndoor()
//        if (nowPaused) {
//            comms.cmdPause()
//            sensorHub.stop()
//        } else {
//            comms.cmdResume()
//            sensorHub.start()
//        }

        sendStateOnce() // also sync snapshot
    }

    fun stopSession() {
        sensorHub.stop()
//        sendCurrentSet()
        stopRepCounting()

        val workout = _ui.value.workout

        // immediate stop command with workout name (mobile triggers onFinishWorkout)
        comms.cmdStopIndoor(workout)

        txJob?.cancel()
        txJob = null
        _ui.value = _ui.value.copy(started = false, isPaused = false)

        sendStateOnce()
    }

    private fun startRepCounting() {
        Log.w("IndoorViewModel", "Start rep counting")
        if (repJob != null) return

        repDetector = RepDetectorFactory.create(_ui.value.workout)

        Log.w("IndoorViewModel", "Creating rep detector")

        repDetector?.reset()
        _reps.value = 0

        repJob = viewModelScope.launch {
            sensorHub.imu.collect { s ->
                val isRep = repDetector?.update(
                    tsMs = s.timestampMs,
                    ax = s.accX, ay = s.accY, az = s.accZ,
                    gx = s.gyroX, gy = s.gyroY, gz = s.gyroZ
                ) == true
                if (isRep) {
                    incReps()
                    Log.w("IndoorViewModel", "Rep detected")
                }
            }
        }
    }

    private fun stopRepCounting() {
        repJob?.cancel()
        repJob = null
    }

//    fun onSensorDataReceived(data: ImuSample) {
//        if (_ui.value.started && !_ui.value.isPaused) {
//            _recordedData.add(data)
//        }
//    }
//
//    fun sendCurrentSet() {
//        Log.w("IndoorViewModel", "Try to send CurrentSet")
//        if (_recordedData.isEmpty()) return
//        // Update state to show spinner
//        val dataToSend = ArrayList(_recordedData)
//        Log.w("IndoorViewModel", "CurrentSet has ${_recordedData.size} samples")
//        _recordedData.clear()
//
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                // Serialize
//                val gson = Gson()
//                val jsonPayload = gson.toJson(dataToSend)
//                val byteArray = jsonPayload.toByteArray(Charsets.UTF_8)
//
//                // Send via MessageClient
//                val nodeClient = Wearable.getNodeClient(applicationContext)
//                val msgClient = Wearable.getMessageClient(applicationContext)
//
//                val nodes = nodeClient.connectedNodes.await()
//                for (node in nodes) {
//                    msgClient.sendMessage(node.id, "/workout_data", byteArray).await()
//                }
//
//                Log.w("Wear", "Sent set with ${dataToSend.size} samples")
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                // Strategy: If fail, maybe put data back?
//                // For now, we log error. Real apps might retry.
//            }
//        }
//    }

    fun incWeight() = updateAndMaybeSend { it.copy(weightKg = it.weightKg + 5) }
    fun decWeight() = updateAndMaybeSend { it.copy(weightKg = maxOf(0, it.weightKg - 5)) }

    fun incReps() = updateAndMaybeSend { it.copy(reps = it.reps + 1) }
    fun decReps() = updateAndMaybeSend { it.copy(reps = maxOf(0, it.reps - 1)) }

    fun incSet() = updateAndMaybeSend { it.copy(set = it.set + 1, reps = 0) }
    fun decSet() = updateAndMaybeSend { it.copy(set = maxOf(1, it.set - 1), reps = 0) }

    private fun startThrottledSync() {
        txJob?.cancel()
        txJob = viewModelScope.launch {
            while (true) {
                val s = _ui.value
                if (s.started) {
                    comms.sendIndoorState(
                        IndoorStatePacket(
                            workout = s.workout,
                            weightKg = s.weightKg,
                            reps = s.reps,
                            set = s.set,
                            isPaused = s.isPaused,
                            started = s.started
                        )
                    )
                }
                delay(100) // 10Hz
            }
        }
    }

    private fun sendStateOnce() {
        val s = _ui.value
        comms.sendIndoorState(
            IndoorStatePacket(
                workout = s.workout,
                weightKg = s.weightKg,
                reps = s.reps,
                set = s.set,
                isPaused = s.isPaused,
                started = s.started
            )
        )
    }

    private inline fun updateAndMaybeSend(block: (IndoorUiState) -> IndoorUiState) {
        _ui.value = block(_ui.value)
        sendStateOnce()
    }

    fun setStartedFromPhone(started: Boolean) {
        _ui.value = _ui.value.copy(started = started)
        sendStateOnce()
        if (!started) {
            txJob?.cancel()
            txJob = null
            _ui.value = _ui.value.copy(isPaused = false)
        }
    }

    override fun onCleared() {
        stopRepCounting()
        super.onCleared()
    }
}