package com.epfl.esl.workoutapp.wear.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.epfl.esl.workoutapp.wear.sensor.ImuSample

@Composable
fun DebugImuCard(imu: ImuSample) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        // ACC column
        Text(
            text = """
                ACC
                x=${imu.accX.format()}
                y=${imu.accY.format()}
                z=${imu.accZ.format()}
            """.trimIndent(),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        // GYRO column
        Text(
            text = """
                GYRO
                x=${imu.gyroX.format()}
                y=${imu.gyroY.format()}
                z=${imu.gyroZ.format()}
            """.trimIndent(),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}
private fun Float.format(): String = "%.2f".format(this)
