package com.epfl.esl.workoutapp.wear.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.epfl.esl.workoutapp.wear.sensor.ImuSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns SensorManager + listeners.
 * Exposes latest HR and IMU sample as StateFlows.
 *
 * You control lifecycle explicitly via start()/stop()
 * (called by Activity/VM).
 */
class SensorHub(
    private val appContext: Context
) : SensorEventListener {

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val hrSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _imu = MutableStateFlow(ImuSample())
    val imu: StateFlow<ImuSample> = _imu

    private var latestAcc = floatArrayOf(0f, 0f, 0f)
    private var latestGyro = floatArrayOf(0f, 0f, 0f)

    private var running = false

    fun start() {
        if (running) return
        running = true

        Log.w("SensorHub", "Starting sensors")

        hrSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val t = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                _heartRate.value = event.values.getOrNull(0)?.toInt() ?: 0
                Log.w("SensorHub", "HR event: ${_heartRate.value}")
            }
            Sensor.TYPE_ACCELEROMETER -> {
                latestAcc = event.values.clone()
                _imu.value = _imu.value.copy(
                    timestampMs = t,
                    accX = latestAcc[0],
                    accY = latestAcc[1],
                    accZ = latestAcc[2],
                    gyroX = latestGyro[0],
                    gyroY = latestGyro[1],
                    gyroZ = latestGyro[2],
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGyro = event.values.clone()
                _imu.value = _imu.value.copy(
                    timestampMs = t,
                    accX = latestAcc[0],
                    accY = latestAcc[1],
                    accZ = latestAcc[2],
                    gyroX = latestGyro[0],
                    gyroY = latestGyro[1],
                    gyroZ = latestGyro[2],
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}