package com.epfl.esl.workoutapp.wear.ui.indoor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import kotlin.math.roundToInt

@Composable
fun SliderWithButtons(
    label: String,
    value: Int,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSlide: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 0..200,
    steps: Int = 0, // 0 = continuous, otherwise discrete
) {
    val haptics = LocalHapticFeedback.current
    val display = remember(value, unit) { if (unit.isBlank()) "$value" else "$value $unit" }

    Card(
        onClick = {},
        enabled = false, // purely visual card
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp)),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface,
            endBackgroundColor = MaterialTheme.colors.surface
        ),
        contentColor = MaterialTheme.colors.onSurface
    ) {
        Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {

            // Header row: label + current value
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.caption1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = display,
                    style = MaterialTheme.typography.title3,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(2.dp))

            // Controls row: [-]  slider  [+]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    onClick = {
                        onMinus()
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    },
                    enabled = value > valueRange.first
                )

                Spacer(Modifier.width(2.dp))

                // Wear Slider expects Float value
                val coerced = value.coerceIn(valueRange)
                Slider(
                    value = coerced.toFloat(),
                    onValueChange = { f ->
                        val newValue = f.roundToInt().coerceIn(valueRange)
                        onSlide(newValue)
                    },
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    steps = steps,
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp),
                )

                Spacer(Modifier.width(2.dp))

                RoundIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    onClick = {
                        onPlus()
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    },
                    enabled = value < valueRange.last
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (enabled) MaterialTheme.colors.primary else MaterialTheme.colors.surface
    val fg = if (enabled) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .size(17.dp)
            .clip(CircleShape)
            .background(bg)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg
            )
        }
    }
}
