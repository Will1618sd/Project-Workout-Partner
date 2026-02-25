package com.epfl.esl.workoutapp.wear.ui.outdoor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.workoutapp.wear.domain.OutdoorActivity
import com.epfl.esl.workoutapp.wear.navigation.SessionStore
import com.epfl.esl.workoutapp.wear.ui.common.components.ActivityHeader
import com.epfl.esl.workoutapp.wear.ui.common.components.PauseStopRow
import com.epfl.esl.workoutapp.wear.ui.indoor.components.StartFullScreenCard
import com.epfl.esl.workoutapp.wear.ui.outdoor.components.MetricRow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn

@Composable
fun OutdoorActivityScreen(
    activity: OutdoorActivity?,
    onStopToHome: () -> Unit,
) {
    val ctx = LocalContext.current.applicationContext
    val vm: OutdoorViewModel = viewModel(factory = OutdoorViewModelFactory(ctx))

    LaunchedEffect(activity) { activity?.let(vm::setActivity) }

    val ui = vm.ui.collectAsState().value
    val startedFromPhone by SessionStore.outdoorStarted.collectAsState(initial = false)

    if (!startedFromPhone && !ui.started) {
        StartFullScreenCard(
            title = ui.title,
            icon = ui.icon,
            onStart = { vm.startSession() }
        )
        return
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        item {
            ActivityHeader(
                title = ui.title,
                icon = ui.icon,
                subtitle = if (ui.paused) "Paused" else "Live"
            )
            Spacer(Modifier.height(6.dp))
        }

        item { MetricRow("TIME", ui.timeText) }
        item { MetricRow("SPEED", "${ui.speedText} ") }
        item { MetricRow("DIST", "${ui.distanceText} ") }

        item {
            Spacer(Modifier.height(8.dp))
            PauseStopRow(
                isPaused = ui.paused,
                onPauseToggle = { vm.togglePause() },
                onStop = {
                    vm.stopSession()
                    onStopToHome()
                }
            )
        }
    }
}