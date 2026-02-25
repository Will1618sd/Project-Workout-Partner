package com.epfl.esl.workoutapp.wear.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.epfl.esl.workoutapp.wear.data.repository.UserInfoRepository
import com.epfl.esl.workoutapp.wear.data.sensors.SensorHub

class HomeViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val repo = UserInfoRepository(appContext)
            val sensors = SensorHub(appContext)
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repo, sensors) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}