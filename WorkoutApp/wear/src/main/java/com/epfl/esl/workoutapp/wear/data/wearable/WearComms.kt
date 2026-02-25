package com.epfl.esl.workoutapp.wear.data.wearable


import android.content.Context
import android.util.Log
import com.epfl.esl.workoutapp.wear.domain.IndoorStatePacket
import com.epfl.esl.workoutapp.wear.domain.IndoorWorkout
import com.epfl.esl.workoutapp.wear.domain.OutdoorActivity
import com.epfl.esl.workoutapp.wear.domain.WearPaths
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearComms(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendIndoorState(packet: IndoorStatePacket) {
        scope.launch {
            try {
                val req = PutDataMapRequest.create(WearPaths.INDOOR_STATE).run {
                    dataMap.putString("workout", packet.workout.name)
                    dataMap.putInt("weightKg", packet.weightKg)
                    dataMap.putInt("reps", packet.reps)
                    dataMap.putInt("set", packet.set)
                    dataMap.putBoolean("isPaused", packet.isPaused)
                    dataMap.putBoolean("started", packet.started)
                    dataMap.putLong("nonce", System.currentTimeMillis()) // force updates
                    asPutDataRequest().setUrgent()
                }
                Wearable.getDataClient(appContext).putDataItem(req).await()
                Log.d("WearComms", "sent indoor_state ${packet.workout} w=${packet.weightKg} r=${packet.reps} s=${packet.set} paused=${packet.isPaused}")
            } catch (e: Exception) {
                Log.e("WearComms", "sendIndoorState failed", e)
            }
        }
    }

    fun sendIndoorCommand(command: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w("WearComms", "No connected nodes for INDOOR_CMD")
                    return@launch
                }
                val client = Wearable.getMessageClient(appContext)
                nodes.forEach { node ->
                    client.sendMessage(node.id, WearPaths.INDOOR_CMD, command.toByteArray()).await()
                }
                Log.w("WearComms", "sent indoor_cmd '$command'")
            } catch (e: Exception) {
                Log.e("WearComms", "sendIndoorCommand failed", e)
            }
        }
    }
    fun cmdStopIndoor(workout: IndoorWorkout) = sendIndoorCommand("STOP|${workout.name}")
    fun cmdPauseIndoor() = sendIndoorCommand("PAUSE")
    fun cmdResumeIndoor() = sendIndoorCommand("RESUME")

    fun sendOutdoorCommand(command: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w("WearComms", "No connected nodes for OUTDOOR_CMD")
                    return@launch
                }
                val client = Wearable.getMessageClient(appContext)
                nodes.forEach { node ->
                    client.sendMessage(node.id, WearPaths.OUTDOOR_CMD, command.toByteArray()).await()
                }
                Log.w("WearComms", "sent outdoor_cmd '$command'")
            } catch (e: Exception) {
                Log.e("WearComms", "sendOutdoorCommand failed", e)
            }
        }
    }
    fun cmdStopOutdoor(activity: OutdoorActivity) = sendOutdoorCommand("STOP|${activity.name}")
    fun cmdStartOutdoor(activity: OutdoorActivity) = sendOutdoorCommand("START|${activity.name}")
    fun cmdPauseOutdoor() = sendOutdoorCommand("PAUSE")
    fun cmdResumeOutdoor() = sendOutdoorCommand("RESUME")
}