package com.epfl.esl.workoutapp.wear.ui.indoor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.workoutapp.wear.ui.indoor.components.StartFullScreenCard
import com.epfl.esl.workoutapp.wear.ui.common.components.PauseStopRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.epfl.esl.workoutapp.wear.domain.IndoorWorkout
import androidx.compose.ui.platform.LocalContext
import com.epfl.esl.workoutapp.wear.navigation.SessionStore
import androidx.compose.runtime.getValue


private enum class IndoorField { WEIGHT, REPS, SET }

@Composable
fun IndoorActivityScreen(
    workout: IndoorWorkout?,
    onStopToHome: () -> Unit,
) {
    val ctx = LocalContext.current.applicationContext
    val vm: IndoorViewModel = viewModel(factory = IndoorViewModelFactory(ctx))

    LaunchedEffect(workout) { workout?.let(vm::setWorkout) }

    val ui = vm.ui.collectAsState().value
    val startedFromPhone by SessionStore.indoorStarted.collectAsState(initial = false)
//    if (ui.isSaving) {
//        Box(
//            modifier = Modifier.fillMaxSize().background(Color.Black),
//            contentAlignment = Alignment.Center
//        ) {
//            CircularProgressIndicator()
//            Text(
//                text = "Saving to Mobile...",
//                modifier = Modifier.padding(top = 60.dp),
//                style = MaterialTheme.typography.caption1
//            )
//        }
//        return // Stop drawing the rest of the UI
//    }
//
//    LaunchedEffect(ui.started, ui.isSaving) {
//        if (!ui.started && !ui.isSaving) {
//            // *Tip: You might need a flag in VM like 'hasFinished' to distinguish
//            // "fresh start" vs "just finished".
//            // Or simply call onStopToHome() inside the onStop callback below if you prefer.
//            onStopToHome()
//        }
//    }

    if (!startedFromPhone && !ui.started) {
        StartFullScreenCard(
            title = ui.title,
            icon = ui.icon,
            onStart = { vm.startSession() } // watch local start still works
        )
        return
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val haptics = LocalHapticFeedback.current

    // Determine what the current "field" is based on the pager page
    val field = when (pagerState.currentPage) {
        0 -> IndoorField.WEIGHT
        1 -> IndoorField.REPS
        else -> IndoorField.SET
    }

    // Helpers that apply + / - depending on current page
    fun dec() {
        when (field) {
            IndoorField.WEIGHT -> vm.decWeight()
            IndoorField.REPS -> vm.decReps()
            IndoorField.SET -> vm.decSet()
        }
    }

    fun inc() {
        when (field) {
            IndoorField.WEIGHT -> vm.incWeight()
            IndoorField.REPS -> vm.incReps()
            IndoorField.SET -> vm.incSet()
        }
    }
    val reps by vm.reps.collectAsState()

    Text("Reps: $reps")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(10.dp)
    ) {
        // Top: icon + title
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = ui.icon,
                contentDescription = ui.title,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(ui.title, style = MaterialTheme.typography.title3)
        }

        // Middle: static minus + pager + static plus
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundIconButton(
                text = "−",
                onClick = {
                    dec()
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                    )
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> CenterMetric(label = "KG", value = "${ui.weightKg}", unit = "kg")
                        1 -> CenterMetric(label = "REPS", value = "${ui.reps}", unit = "")
                        2 -> CenterMetric(label = "SET", value = "${ui.set}", unit = "")
                    }
                }
            }

            RoundIconButton(
                text = "+",
                onClick = {
                    inc()
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                    )
                }
            )
        }

        // Bottom: Pause / Stop
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PauseStopRow(
                isPaused = ui.isPaused,
                onPauseToggle = { vm.togglePause() },
                onStop = {
                    vm.stopSession()
                    onStopToHome()
                }
            )
        }
    }
}

/* ------------------ tiny UI pieces ------------------ */

@Composable
private fun CenterMetric(label: String, value: String, unit: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.65f)
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.display3,
                textAlign = TextAlign.Center
            )

            if (unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.surface)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface
            )
        ) {
            Text(text = text, style = MaterialTheme.typography.title2)
        }
    }
}