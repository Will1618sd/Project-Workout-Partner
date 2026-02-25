package com.epfl.esl.workoutapp.mobile.service

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendMessage(path: String, payload: ByteArray) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w("WearCmd", "No connected Wear nodes. path='$path'")
                    return@launch
                }
                val messageClient = Wearable.getMessageClient(appContext)
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, payload).await()
                }
            } catch (e: Exception) {
                Log.e("WearCmd", "Failed to send message path='$path'", e)
            }
        }
    }

    fun sendCommand(command: String) {
        sendMessage(WearPaths.COMMAND, command.toByteArray())
    }

    fun sendSelectOutdoor(activityName: String) {
        sendCommand("Select|OUTDOOR|$activityName")
    }

    fun sendSelectIndoor(workoutName: String) {
        sendCommand("Select|INDOOR|$workoutName")
    }

    fun sendStartOutdoor(activityName: String) {
        sendCommand("Start|OUTDOOR|$activityName")
    }

    fun sendStartIndoor(workoutName: String) {
        sendCommand("Start|INDOOR|$workoutName")
    }

    fun sendStop() {
        sendCommand("Stop")
    }

    fun sendShowSummary(
        type: String,
        title: String,
        durationSec: Int,
        totalReps: Int? = null,
        totalSets: Int? = null,
        distanceMeters: Int? = null,
        avgHr: Int? = null,
        maxHr: Int? = null,
        avgPaceSecPerKm: Int? = null,
    ) {
        val payload = buildString {
            append(type).append("|")
            append(title).append("|")
            append(durationSec).append("|")
            append(totalSets ?: -1).append("|")
            append(totalReps ?: -1).append("|")
            append(distanceMeters ?: -1).append("|")
            append(avgHr ?: -1).append("|")
            append(maxHr ?: -1).append("|")
            append(avgPaceSecPerKm ?: -1)
        }.toByteArray()

        sendMessage("/show_summary", payload)
    }
}


@Composable
fun WearStreamingEffect(
    dataClient: DataClient,
    messageClient: com.google.android.gms.wearable.MessageClient,
    dataListener: DataClient.OnDataChangedListener,
    msgListener: com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener,
    wearController: WearController
) {
    DisposableEffect(dataListener, msgListener) {
        try { dataClient.addListener(dataListener) } catch (e: Exception) {
            Log.w("WearCmd", "Failed to add data listener", e)
        }
        try { messageClient.addListener(msgListener) } catch (e: Exception) {
            Log.w("WearCmd", "Failed to add msg listener", e)
        }
        onDispose {
            try { dataClient.removeListener(dataListener) } catch (e: Exception) {
                Log.w("WearCmd", "Failed to remove data listener", e)
            }
            try { messageClient.removeListener(msgListener) } catch (e: Exception) {
                Log.w("WearCmd", "Failed to msg listener", e)
            }
        }
    }
}