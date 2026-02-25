package com.epfl.esl.workoutapp.mobile.ui.activity

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.workoutapp.mobile.data.model.ImuSample
import com.epfl.esl.workoutapp.mobile.service.WearController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import android.app.Activity
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke





@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun IndoorActivityScreen(
    userKey: String,
    onWorkoutStart: (WORKOUT) -> Unit,
    onBackButton: () -> Unit,
    modifier: Modifier = Modifier,
    indoorActivityViewModel: IndoorActivityViewModel = viewModel()
) {
    val selectedWorkout by indoorActivityViewModel.selectedWorkout.observeAsState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Choose a workout", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(16.dp))

        WorkoutSelector(
            selectedWorkout = selectedWorkout,
            onWorkoutSelected = { workout ->
                indoorActivityViewModel.setSelectedWorkout(workout)
            }
        )
        Spacer(Modifier.height(24.dp))

        selectedWorkout?.let { workout ->

            Spacer(Modifier.height(24.dp))

            Button(onClick = {
                indoorActivityViewModel.setSelectedWorkout(workout)
                onWorkoutStart(workout)
            }) {
                Text("Start ${workout.label}")
            }

            Button(onClick = onBackButton) {
                Text("Back")
            }
        }


        Spacer(Modifier.height(24.dp))


    }
}

@Composable
fun WorkoutSelector(
    selectedWorkout: WORKOUT?,
    onWorkoutSelected: (WORKOUT) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        WORKOUT.values().forEach { workout ->
            FilterChip(
                selected = selectedWorkout == workout,
                onClick = { onWorkoutSelected(workout) },
                label = { Text(workout.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WorkoutSessionScreen(
    dataClient: DataClient,
    workout: WORKOUT,
    onFinishWorkout: (sets: List<WorkoutSet>, startTime: Long, endTime: Long) -> Unit,
    indoorActivityViewModel: IndoorActivityViewModel,
    sessionViewModel: WorkoutSessionViewModel,
    wearController: WearController,
) {
    val context = LocalContext.current

    val reps by sessionViewModel.reps.observeAsState(0)
    val setNumber by sessionViewModel.sets.observeAsState(1)
    val timer by sessionViewModel.time.observeAsState("00:00")
    val weightKg by sessionViewModel.weightKg.observeAsState(0)

    // Debug data coming from Wear listener in VM (listener is attached in NavGraph)
    val heartRate by indoorActivityViewModel.heartRate.observeAsState(0)
    val imu by indoorActivityViewModel.latestImu.observeAsState(
        ImuSample(0, 0f, 0f, 0f, 0f, 0f, 0f)
    )

    val sessionStartedState = androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val wearIndoorState by indoorActivityViewModel.indoorState.collectAsState()
    val sessionStarted = sessionStartedState.value

    LaunchedEffect(wearIndoorState.started) {
        if (wearIndoorState.started) {
            Log.w("LAUNCHED", "0: sessionStartedState.value = true")
            sessionStartedState.value = true
        }
    }

    LaunchedEffect(
        sessionStarted,
        wearIndoorState.reps,
        wearIndoorState.set,
        wearIndoorState.weightKg
    ) {
        if (!sessionStarted) return@LaunchedEffect
        Log.w("LAUNCHED", "1: wearIndoorState.reps=${wearIndoorState.reps}")

        sessionViewModel.setReps(wearIndoorState.reps)
        sessionViewModel.setSet(wearIndoorState.set)
        sessionViewModel.setWeightKg(wearIndoorState.weightKg)
    }
   /* LifecycleResumeEffect(workout) {
        dataClient.addListener(indoorActivityViewModel)

        onPauseOrDispose {
            dataClient.removeListener(indoorActivityViewModel)

            val activityHost = context as? Activity
            val isRotation = activityHost?.isChangingConfigurations == true

            if (!isRotation) {
                // Only stop wear session if YOU started it from mobile UI
                if (sessionStarted && !wearIndoorState.started) {
                    indoorActivityViewModel.sendCommandToWear("Stop", context)
                }
            }
        }
    }*/

    LaunchedEffect(sessionStarted, wearIndoorState.started) {
        if (sessionStarted && !wearIndoorState.started) {
            Log.w("LAUNCHED", "2: wearController: Sending Start|INDOOR|${workout.name} command")
            wearController.sendStartIndoor(workout.name)
        }
    }


    LaunchedEffect(workout, sessionStarted) {
        if (sessionStarted){
            Log.w("LAUNCHED", "3: sessionViewModel.startSession($workout)")
            sessionViewModel.startSession(workout)
        }
    }

//    // To modify for automatic detection of exercise and nb of reps
//    LaunchedEffect(Unit) {
//        indoorActivityViewModel.prediction.collect { result ->
//            sessionViewModel.updateRepsFromWear(result.reps)
//        }
//    }

    DisposableEffect(workout, sessionStarted) {
        onDispose {
           /* val activityHost = context as? Activity
            val isRotation = activityHost?.isChangingConfigurations == true

            // Do not stop the timer on rotation; only stop when actually leaving
            if (!isRotation) {
                sessionViewModel.finishWorkout()
            }*/

            Log.w("DISPOSED", "0: sessionViewModel.finishWorkout()")
            sessionViewModel.finishWorkout()
            if (sessionStarted) {
                Log.w("DISPOSED", "1: wearController: Sending STOP|${workout.name} command")
                wearController.sendCommand("STOP|${workout.name}")
            }
        }
    }

    if (!sessionStarted){
        PreWorkoutStartScreen(workout.label,
            onStart = {sessionStartedState.value = true}
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(workout.label, style = MaterialTheme.typography.headlineMedium)
        Text(timer, style = MaterialTheme.typography.displayMedium)

        // Set and Reps
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Set $setNumber")
            Text("Reps: $reps", style = MaterialTheme.typography.headlineLarge)
        }
        // Weights
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Weight: $weightKg kg")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { sessionViewModel.setWeightKg(weightKg - 5) },
                    border = BorderStroke(1.dp, Color.Black),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Black
                    )
                ) {
                    Text("-5")
                }
                OutlinedButton(
                    onClick = { sessionViewModel.setWeightKg(weightKg + 5) },
                    border = BorderStroke(1.dp, Color.Black),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Black
                    )
                ) {
                    Text("+5")
                }
            }
        }

        Column {

            OutlinedButton(
                onClick = { sessionViewModel.addRep() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Black
                )
            ) {
                Text(
                    "Add Rep",
                    style = MaterialTheme.typography.titleMedium
                )
            }


            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { sessionViewModel.nextSet() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Black
                )
            ) {
                Text(
                    "Next Set",
                    style = MaterialTheme.typography.titleMedium
                )
            }


            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val (setsToSave, start, end) = sessionViewModel.buildSummary()
                    sessionViewModel.finishWorkout()
                    onFinishWorkout(setsToSave, start, end)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF4B33),
                    contentColor = Color.White
                )
            ) {
                Text(
                    "Finish Workout",
                    style = MaterialTheme.typography.titleMedium
                )
            }

        }
    }
}

@Composable
private fun PreWorkoutStartScreen(
    workoutLabel: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready?", style = MaterialTheme.typography.displaySmall)

        Spacer(Modifier.height(12.dp))

        Text(
            workoutLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF4B33),
                contentColor = Color.White
            )
        ) {
            Text("Start", style = MaterialTheme.typography.titleLarge)
        }
    }
}
