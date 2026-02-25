package com.epfl.esl.workoutapp.wear.ui.indoor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.epfl.esl.workoutapp.wear.data.wearable.WearComms
import com.epfl.esl.workoutapp.wear.data.sensors.SensorHub

class IndoorViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IndoorViewModel::class.java)) {
            val sensorHub = SensorHub(appContext)
            @Suppress("UNCHECKED_CAST")
            return IndoorViewModel(comms = WearComms(appContext),
                sensorHub = sensorHub) as T
//            return IndoorViewModel(
//                comms = WearComms(appContext),
//                sensorHub = sensorHub,
//                appContext
//            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}