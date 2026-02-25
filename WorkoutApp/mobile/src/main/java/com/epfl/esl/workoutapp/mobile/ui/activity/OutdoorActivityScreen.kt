package com.epfl.esl.workoutapp.mobile.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.workoutapp.R
import com.epfl.esl.workoutapp.mobile.ui.theme.WorkoutAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import android.app.Activity
import android.util.Log
import com.epfl.esl.workoutapp.mobile.ui.activity.OutdoorActivityViewModel.OutdoorEvent


@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OutdoorActivityScreen(
    userKey: String,
    activity: ACTIVITY,
    dataClient: DataClient,
    onFinishOutdoor: (
        activityName: String,
        durationSec: Int,
        distanceMeters: Int,
        avgHr: Int,
        maxHr: Int,
        avgPaceSecPerKm: Int
    ) -> Unit,
    modifier: Modifier = Modifier,
    outdoorActivityViewModel: OutdoorActivityViewModel = viewModel()
)
 {
    val context = LocalContext.current
    val isRecording = remember { mutableStateOf(false) }
    val sessionStartMs = remember { mutableStateOf<Long?>(null) }
//  val device by remember { mutableStateOf(DEVICE.SMARTWATCH) }

    val heartRate by outdoorActivityViewModel.heartRate.observeAsState(initial = 0)
    val pointsData by outdoorActivityViewModel.heartRateList
        .observeAsState(initial = listOf())

    val sessionState by outdoorActivityViewModel.sessionState
        .observeAsState(initial = OutdoorSessionState.IDLE)

     val elapsedSec by outdoorActivityViewModel.elapsedSec.observeAsState(initial = 0)
    val distanceMeters by outdoorActivityViewModel.distanceMetersLive.observeAsState(initial = 0)
    val speedMps by outdoorActivityViewModel.speedMpsLive.observeAsState(initial = 0f)

    val speedKmh = speedMps * 3.6f
    val distanceKm = distanceMeters / 1000f
    val mm = elapsedSec / 60
    val ss = elapsedSec % 60

     val traceLatLng by outdoorActivityViewModel.traceLatLngLive
         .observeAsState(initial = emptyList())

    val locationData by outdoorActivityViewModel
        .locationData.observeAsState(initial = LocationData(
            46.5197,
            6.6323,
            "Lausanne"
        ))

     val cameraPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )


    LifecycleResumeEffect(0) {
        dataClient.addListener(outdoorActivityViewModel)
        outdoorActivityViewModel.getLastLocation(context) // just to center map initially

        onPauseOrDispose {
            dataClient.removeListener(outdoorActivityViewModel)

            val activityHost = context as? Activity
            val isRotation = activityHost?.isChangingConfigurations == true

            // If this dispose is due to rotation, do NOT pause/stop the session.
            if (!isRotation) {
                if (sessionState != OutdoorSessionState.IDLE) {
                    outdoorActivityViewModel.pauseOutdoorSession()
                    outdoorActivityViewModel.sendCommandToWear("Stop", context)
                }
            }
        }

    }

     LaunchedEffect(Unit) {
         outdoorActivityViewModel.events.collect { e ->
             when (e) {
                 is OutdoorEvent.Stop -> {
                     val (start, end, distMeters) =
                         outdoorActivityViewModel.finishOutdoorSession()

                     // (Optional) tell wear “Stop” for UI sync; you may also skip this to avoid loops
                     outdoorActivityViewModel.sendCommandToWear("Stop", context)

                     val (avgHr, maxHr) =
                         outdoorActivityViewModel.computeHrSummary()

                     val durationSec =
                         ((end - start) / 1000).toInt().coerceAtLeast(0)

                     val avgPaceSecPerKm =
                         if (distMeters > 0 && durationSec > 0)
                             (durationSec * 1000) / distMeters
                         else 0

                     outdoorActivityViewModel.setPendingOutdoorSession(
                         PendingOutdoorSession(
                             activity = e.activity,
                             startTime = start,
                             endTime = end,
                             distanceMeters = distMeters,
                             avgHeartRate = avgHr,
                             maxHeartRate = maxHr
                         )
                     )

                     onFinishOutdoor(
                         e.activity.name,
                         durationSec,
                         distMeters,
                         avgHr,
                         maxHr,
                         avgPaceSecPerKm
                     )
                 }

                 OutdoorEvent.Pause -> TODO()
                 OutdoorEvent.Start -> TODO()
             }
         }
     }
//     LaunchedEffect(sessionStarted, wearIndoorState.started) {
//         if (sessionStarted && !wearIndoorState.started) {
//             Log.w("LAUNCHED", "2: wearController: Sending Start|INDOOR|${workout.name} command")
//             wearController.sendStartIndoor(workout.name)
//         }
//     }


     val cameraPositionState = rememberCameraPositionState()
    val markerState = rememberMarkerState()
    val currentPosition = LatLng(locationData.latitude, locationData.longitude)
    LaunchedEffect(key1 = currentPosition) {
        cameraPositionState.position =
            CameraPosition.fromLatLngZoom(currentPosition, 17f)
        markerState.position = currentPosition
    }

    Box(modifier = modifier.fillMaxSize()) {

        // --- Fullscreen map (GPS covers whole screen) ---
        if (cameraPermissionState.hasPermission) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = true),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false)
            ){
                if (traceLatLng.size >= 2) {
                    Polyline(
                        points = traceLatLng,
                        color = Color(0xFFFF4B33),
                        width = 6f
                        )
                }

            }
        } else {
            // Permission prompt overlay (kept simple)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.location_permission_text))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text(text = stringResource(R.string.location_permission_button_text))
                }
            }
        }

        // --- Bottom overlay: stats + play button (under the stats row) + other actions ---
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Time / Speed / Distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Time", style = MaterialTheme.typography.labelLarge)
                        Text(
                            String.format("%02d:%02d", mm, ss),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed", style = MaterialTheme.typography.labelLarge)
                        Text(
                            String.format("%.1f km/h", speedKmh),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Distance", style = MaterialTheme.typography.labelLarge)
                        Text(
                            String.format("%.2f km", distanceKm),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
//                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                        Text("Heart Rate", style = MaterialTheme.typography.labelLarge)
//                        Text(
//                            String.format("%.1f", heartRate),
//                            style = MaterialTheme.typography.titleLarge
//                        )
//                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    when (sessionState) {

                        // -------- BEFORE START --------
                        OutdoorSessionState.IDLE -> {
                            FloatingActionButton(
                                onClick = {
                                    outdoorActivityViewModel.startOutdoorSession(context)
                                    outdoorActivityViewModel.sendCommandToWear("Start", context)
                                },
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                containerColor = Color(0xFFFF4B33)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Start",
                                    tint = Color.White
                                )
                            }
                        }

                        // -------- RUNNING / PAUSED --------
                        OutdoorSessionState.RUNNING,
                        OutdoorSessionState.PAUSED -> {

                            // Play / Pause
                            FloatingActionButton(
                                onClick = {
                                    if (sessionState == OutdoorSessionState.RUNNING) {
                                        outdoorActivityViewModel.pauseOutdoorSession()
                                        //outdoorActivityViewModel.sendCommandToWear("Stop", context)
                                    } else {
                                        outdoorActivityViewModel.resumeOutdoorSession(context)
                                        //outdoorActivityViewModel.sendCommandToWear("Start", context)
                                    }
                                },
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                containerColor = Color(0xFFFF4B33)
                            ) {
                                Icon(
                                    imageVector = if (sessionState == OutdoorSessionState.RUNNING)
                                        Icons.Filled.Pause
                                    else
                                        Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Finish
                            Button(
                                onClick = {
                                    val (start, end, distMeters) =
                                        outdoorActivityViewModel.finishOutdoorSession()

                                    outdoorActivityViewModel.sendCommandToWear("Stop", context)

                                    val (avgHr, maxHr) =
                                        outdoorActivityViewModel.computeHrSummary()

                                    val durationSec =
                                        ((end - start) / 1000).toInt().coerceAtLeast(0)

                                    val avgPaceSecPerKm =
                                        if (distMeters > 0 && durationSec > 0)
                                            (durationSec * 1000) / distMeters
                                        else 0

                                    outdoorActivityViewModel.setPendingOutdoorSession(
                                        PendingOutdoorSession(
                                            activity = activity,
                                            startTime = start,
                                            endTime = end,
                                            distanceMeters = distMeters,
                                            avgHeartRate = avgHr,
                                            maxHeartRate = maxHr
                                        )
                                    )

                                    onFinishOutdoor(
                                        activity.name,
                                        durationSec,
                                        distMeters,
                                        avgHr,
                                        maxHr,
                                        avgPaceSecPerKm
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Flag,
                                    contentDescription = "Finish"
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Finish")
                            }
                        }
                    }
                }


            }
        }
    }

}