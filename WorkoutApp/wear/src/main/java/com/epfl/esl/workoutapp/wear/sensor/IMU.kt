package com.epfl.esl.workoutapp.wear.sensor

data class ImuSample(
    val timestampMs: Long=0,
    val accX: Float=11f,
    val accY: Float=11f,
    val accZ: Float=11f,
    val gyroX: Float=127f,
    val gyroY: Float=127f,
    val gyroZ: Float=127f
)
fun ImuSample.pretty(): String =
    """
    ACC
      x=${"%.2f".format(accX)}
      y=${"%.2f".format(accY)}
      z=${"%.2f".format(accZ)}
    GYRO
      x=${"%.2f".format(gyroX)}
      y=${"%.2f".format(gyroY)}
      z=${"%.2f".format(gyroZ)}
    """.trimIndent()