package com.epfl.esl.workoutapp.wear.ui.outdoor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.epfl.esl.workoutapp.wear.data.repository.OutdoorMetricsRepository
import com.epfl.esl.workoutapp.wear.data.wearable.WearComms
import com.google.android.gms.wearable.Wearable

class OutdoorViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OutdoorViewModel::class.java)) {
            val dataClient = Wearable.getDataClient(appContext)
            val repo = OutdoorMetricsRepository(dataClient)
            val comms = WearComms(appContext)
            @Suppress("UNCHECKED_CAST")
            return OutdoorViewModel(repo = repo, comms = comms) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}