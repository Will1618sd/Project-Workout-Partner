package com.epfl.esl.workoutapp.wear.data.wearable

import android.util.Log
import androidx.navigation.NavController
import com.epfl.esl.workoutapp.wear.domain.ActivityType
import com.epfl.esl.workoutapp.wear.navigation.NavEvents
import com.epfl.esl.workoutapp.wear.navigation.SessionStore
import com.epfl.esl.workoutapp.wear.domain.WearPaths
import com.epfl.esl.workoutapp.wear.domain.CommandParser
import com.epfl.esl.workoutapp.wear.navigation.Routes
import com.epfl.esl.workoutapp.wear.ui.common.WearSummary
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class WearCommandService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.w("WEAR_CMD", "WearCommandService created")
    }

//    override fun onMessageReceived(event: MessageEvent) {
//        val msg = String(event.data)
//        Log.w("WEAR_CMD", "rx path=${event.path} msg=$msg")
//
//        if (event.path !in setOf(
//                WearPaths.INDOOR_CMD,
//                WearPaths.OUTDOOR_CMD,
//                WearPaths.COMMAND
//            )
//        ) return
//
//        when (val cmd = CommandParser.parse(msg)) {
//
//            is CommandParser.Command.Select -> {
//                Log.w("WEAR_CMD", "Select received: $cmd")
//                SessionStore.set(cmd.type)
//                NavEvents.tryEmit(NavEvents.Event.StartSessionEvent(cmd.type))
//                if (cmd.type is ActivityType.Indoor) {
//                    NavEvents.tryEmit(NavEvents.Event.SetIndoorStarted(false))
//                }
//            }
//
//            is CommandParser.Command.Start -> {
//                Log.w("WEAR_CMD", "Start received: $cmd")
//                SessionStore.set(cmd.type)
//                NavEvents.tryEmit(NavEvents.Event.StartSessionEvent(cmd.type))
//                if (cmd.type is ActivityType.Indoor) {
//                    NavEvents.tryEmit(NavEvents.Event.SetIndoorStarted(true))
//                }
//            }
//
//            CommandParser.Command.Stop -> {
//                Log.w("WEAR_CMD", "Stop received")
//                SessionStore.clear()
//                NavEvents.tryEmit(NavEvents.Event.StopSessionEvent)
//            }
//
//            CommandParser.Command.Unknown -> {
//                Log.w("WEAR_CMD", "Unknown command: $msg")
//            }
//        }
//    }

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val msg = String(event.data)
        Log.w("WEAR_CMD", "rx path=$path msg=$msg")

        // 1) Handle summary as a special-case message (JSON), no CommandParser involved
        if (path == "/show_summary") {
            try {
                val root = org.json.JSONObject(msg)

                val summary = WearSummary(
                    type = root.optString("type", "outdoor"),
                    title = root.optString("title", "Running"),
                    durationSec = root.optInt("durationSec", 120),
                    totalSets = root.takeOptInt("totalSets"),
                    totalReps = root.takeOptInt("totalReps"),
                    distanceMeters = root.optInt("distanceMeters", 600),
                    avgHr = root.optInt("avgHr", 80),
                    maxHr = root.optInt("maxHr", 130),
                    avgPaceSecPerKm = root.optInt("avgPaceSecPerKm", 200),
                )

                SessionStore.summary.value = summary
                Log.w("WEAR_SUMMARY", "Stored summary: $summary")
            } catch (e: Exception) {
                Log.e("WEAR_SUMMARY", "Failed to parse /show_summary payload", e)
            }
            return
        }

        // 2) Existing command paths
        if (path !in setOf(
                WearPaths.INDOOR_CMD,
                WearPaths.OUTDOOR_CMD,
                WearPaths.COMMAND
            )
        ) return

        // 3) Existing command parsing + actions
        when (val cmd = CommandParser.parse(msg)) {

            is CommandParser.Command.Select -> {
                Log.w("WEAR_CMD", "Select received: $cmd")
                SessionStore.set(cmd.type)
                NavEvents.tryEmit(NavEvents.Event.StartSessionEvent(cmd.type))
                if (cmd.type is ActivityType.Indoor) {
                    NavEvents.tryEmit(NavEvents.Event.SetIndoorStarted(false))
                }
            }

            is CommandParser.Command.Start -> {
                Log.w("WEAR_CMD", "Start received: $cmd")
                SessionStore.set(cmd.type)
                NavEvents.tryEmit(NavEvents.Event.StartSessionEvent(cmd.type))
                if (cmd.type is ActivityType.Indoor) {
                    NavEvents.tryEmit(NavEvents.Event.SetIndoorStarted(true))
                }
            }

            CommandParser.Command.Stop -> {
                Log.w("WEAR_CMD", "Stop received")
                SessionStore.clear()
                NavEvents.tryEmit(NavEvents.Event.StopSessionEvent)
            }

            CommandParser.Command.Unknown -> {
                Log.w("WEAR_CMD", "Unknown command: $msg")
            }
        }
    }

    /** Helper: treat missing or null JSON field as Kotlin null */
    private fun org.json.JSONObject.takeOptInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }
}