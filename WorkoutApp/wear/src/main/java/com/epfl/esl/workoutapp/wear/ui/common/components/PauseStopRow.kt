package com.epfl.esl.workoutapp.wear.ui.common.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@Composable
fun PauseStopRow(
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundActionButton(
            icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            label = if (isPaused) "Resume" else "Pause",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPauseToggle()
            }
        )

        RoundActionButton(
            icon = Icons.Filled.Stop,
            label = "Stop",
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStop()
            },
            danger = true
        )
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val bg = if (danger) Color(0xFFD32F2F) else MaterialTheme.colors.surface
    val fg = if (danger) Color.White else MaterialTheme.colors.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(min = 64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = bg,
                    contentColor = fg
                )
            ) {
                Icon(imageVector = icon, contentDescription = label)
            }
        }
    }
}