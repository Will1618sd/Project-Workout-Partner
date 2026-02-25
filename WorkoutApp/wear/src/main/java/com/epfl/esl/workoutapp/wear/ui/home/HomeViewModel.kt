package com.epfl.esl.workoutapp.wear.ui.home

import android.graphics.Bitmap
import com.epfl.esl.workoutapp.wear.sensor.ImuSample
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epfl.esl.workoutapp.wear.data.repository.UserInfoRepository
import com.epfl.esl.workoutapp.wear.data.sensors.SensorHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "—",
    val photo: Bitmap? = null,
    val heartRate: Int? = null,
    val imu: ImuSample = ImuSample(),
    val showImuDebug: Boolean = true, // turn off later
)

class HomeViewModel(
    private val userInfoRepo: UserInfoRepository,
    private val sensorHub: SensorHub,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui

    init {
        // Start producers
        userInfoRepo.start()
        sensorHub.start()

        // Combine flows into one UI state
        viewModelScope.launch {
            combine(
                userInfoRepo.userInfo,
                sensorHub.heartRate,
                sensorHub.imu
            ) { userInfo, hr, imu ->
                HomeUiState(
                    username = userInfo.username,
                    photo = userInfo.photo,
                    heartRate = hr,
                    imu = imu,
                    showImuDebug = true
                )
            }.collect { _ui.value = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userInfoRepo.stop()
        sensorHub.stop()
    }
}