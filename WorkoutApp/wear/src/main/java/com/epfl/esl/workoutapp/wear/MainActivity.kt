package com.epfl.esl.workoutapp.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import com.epfl.esl.workoutapp.wear.domain.ActivityType
import com.epfl.esl.workoutapp.wear.navigation.NavEvents
import com.epfl.esl.workoutapp.wear.navigation.Routes
import com.epfl.esl.workoutapp.wear.navigation.SessionStore
import com.epfl.esl.workoutapp.wear.navigation.WearNavGraph
import com.epfl.esl.workoutapp.wear.ui.common.theme.WorkoutAppTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutAppTheme {
                val nav = rememberNavController()

                // Listen for Start/Stop and navigate
                LaunchedEffect(Unit) {
                    NavEvents.events.collectLatest { event ->
                        when (event) {

                            is NavEvents.Event.StartSessionEvent -> {
                                SessionStore.set(event.type)

                                when (event.type) {
                                    is ActivityType.Indoor -> nav.navigate(Routes.INDOOR) { launchSingleTop = true }
                                    is ActivityType.Outdoor -> nav.navigate(Routes.OUTDOOR) { launchSingleTop = true }
                                }
                            }
                            is NavEvents.Event.SetIndoorStarted -> {
                                SessionStore.setIndoorStarted(event.started)
                            }

                            NavEvents.Event.StopSessionEvent -> {
                                nav.popBackStack(Routes.HOME, inclusive = false)
                            }
                        }
                    }
                }

                WearNavGraph(navController = nav)
            }
        }
    }
}