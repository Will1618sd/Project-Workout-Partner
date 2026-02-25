package com.epfl.esl.workoutapp.mobile.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState



@Composable
fun OutdoorSummaryScreen(
    activityName: String,
    durationSec: Int,
    distanceMeters: Int,
    avgHeartRate: Int,
    maxHeartRate: Int,
    avgPaceSecPerKm: Int,
    onShare: (String) -> Unit,
    onDone: () -> Unit,
    onDiscardSession: () -> Unit,
    traceLatLng: List<LatLng> = emptyList(),
    modifier: Modifier = Modifier
) {
    val minutes = durationSec / 60
    val seconds = durationSec % 60
    val km = distanceMeters / 1000f

    // pace: sec/km -> m:ss /km
    val paceMin = if (avgPaceSecPerKm > 0) avgPaceSecPerKm / 60 else 0
    val paceSec = if (avgPaceSecPerKm > 0) avgPaceSecPerKm % 60 else 0
    val paceText = if (avgPaceSecPerKm > 0) String.format("%d:%02d /km", paceMin, paceSec) else "—"

    val shareText = buildString {
        append("Activity complete: $activityName\n")
        append("Duration: ${minutes}m ${seconds}s\n")
        append(String.format("Distance: %.2f km\n", km))
        append("Avg pace: $paceText\n")
        append("HR: avg $avgHeartRate | max $maxHeartRate\n")
        append("\nGood job. Consistency wins.")
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Good job",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "You completed $activityName.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (traceLatLng.isNotEmpty()) {
                        val center = traceLatLng.last()
                        val camState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(center, 16f)
                        }

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(220.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = camState,
                                properties = MapProperties(mapType = MapType.NORMAL)
                            ) {
                                if (traceLatLng.size >= 2) {
                                    Polyline(
                                        points = traceLatLng,
                                        color = Color(0xFFFF4B33),
                                        width = 6f
                                    )
                                }
                            }
                        }
                    }

                    SummaryStatCard(title = "Duration", value = "${minutes}m ${seconds}s", icon = Icons.Filled.Schedule)
                    SummaryStatCard(title = "Distance", value = String.format("%.2f km", km), icon = Icons.Filled.Straighten)
                    SummaryStatCard(title = "Avg pace", value = paceText, icon = Icons.Filled.Speed)
                    SummaryStatCard(title = "Heart rate", value = "avg $avgHeartRate • max $maxHeartRate", icon = Icons.Filled.Favorite)
                }

                Button(
                    onClick = { onShare(shareText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text("Share activity")
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Done") }

                val showDiscardDialog = remember { mutableStateOf(false) }

                TextButton(
                    onClick = { showDiscardDialog.value = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Discard session") }

                if (showDiscardDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showDiscardDialog.value = false },
                        title = { Text("Discard session?") },
                        text = { Text("This activity will not be saved. This action cannot be undone.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDiscardDialog.value = false
                                    onDiscardSession()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Discard") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiscardDialog.value = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    value: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
