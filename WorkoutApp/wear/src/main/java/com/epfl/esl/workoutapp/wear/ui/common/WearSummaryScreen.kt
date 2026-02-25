package com.epfl.esl.workoutapp.wear.ui.common

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

val DefaultWearSummary = WearSummary(
    type = "outdoor",
    title = "Running",
    durationSec = 120,
    distanceMeters = 600,
    avgHr = 80,
    maxHr = 130,
    avgPaceSecPerKm = 200,
)
data class WearSummary(
    val type: String,
    val title: String,
    val durationSec: Int,
    val totalSets: Int? = null,
    val totalReps: Int? = null,
    val distanceMeters: Int? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val avgPaceSecPerKm: Int? = null,
)

@Composable
fun WearSummaryScreen(summary: WearSummary) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface),
        contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp, start = 10.dp, end = 10.dp)
    ) {

        item {
            Text(
                text = summary.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        item {
            Text(
                text = "Duration: ${summary.durationSec} s",
                color = Color.White,
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
        }

        @Composable
        fun StatChip(label: String, value: String) {
            Chip(
                onClick = {},
                enabled = false,
                label = { Text(text = label, color = Color.White,) },
                secondaryLabel = { Text(value, color = Color.White,) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (summary.type == "indoor") {
            summary.totalSets?.let { sets ->
                item { StatChip(label = "Total sets", value = sets.toString()) }
            }
            summary.totalReps?.let { reps ->
                item { StatChip(label = "Total reps", value = reps.toString()) }
            }
        } else {
            summary.distanceMeters?.let { meters ->
                item { StatChip(label = "Distance", value = "${meters / 1000} km") }
            }
            summary.avgPaceSecPerKm?.let { pace ->
                item { StatChip(label = "Avg pace", value = "$pace s/km") }
            }
        }

        summary.avgHr?.let { hr ->
            item { StatChip(label = "Avg HR", value = hr.toString()) }
        }
        summary.maxHr?.let { hr ->
            item { StatChip(label = "Max HR", value = hr.toString()) }
        }

    }
}

@Composable
fun WearSummaryLoadingScreen(onCancel: () -> Unit) {
    Scaffold( timeText = { TimeText() } ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.surface)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text(text = "Loading...")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onCancel) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}