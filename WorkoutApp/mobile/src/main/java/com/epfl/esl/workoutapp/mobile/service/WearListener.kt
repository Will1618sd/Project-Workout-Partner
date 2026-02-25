package com.epfl.esl.workoutapp.mobile.service

import com.epfl.esl.workoutapp.mobile.data.model.ImuSample
import com.epfl.esl.workoutapp.mobile.logic.processExerciseSet
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DataLayerListenerService : WearableListenerService() {

    // Create a scope for this service
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/workout_data") {
            // Processing might take time, so we launch a coroutine
            serviceScope.launch {
                try {
                    val jsonString = String(messageEvent.data, Charsets.UTF_8)
                    val gson = Gson()
                    val type = object : TypeToken<List<ImuSample>>() {}.type
                    val rawData: List<ImuSample> = gson.fromJson(jsonString, type)

                    println("Received ${rawData.size} samples. Starting processing...")

                    // Run the heavy math (processExerciseSet is CPU intensive)
                    processExerciseSet(applicationContext, rawData)

                    // Note: processExerciseSet prints the result.
                    // To show it on the Phone UI, you would send a broadcast or update a database here.

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional: Cancel jobs if service is destroyed,
        // though for a "fire and forget" processing task, you might want it to finish.
    }
}