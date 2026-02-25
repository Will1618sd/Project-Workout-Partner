package com.epfl.esl.workoutapp.wear.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import com.epfl.esl.workoutapp.wear.ui.home.HomeScreen
import com.epfl.esl.workoutapp.wear.ui.indoor.IndoorActivityScreen
import com.epfl.esl.workoutapp.wear.ui.outdoor.OutdoorActivityScreen
import androidx.compose.runtime.collectAsState
import com.epfl.esl.workoutapp.wear.domain.ActivityType

object Routes {
    const val HOME = "home"
    const val INDOOR = "indoor"
    const val OUTDOOR = "outdoor"
    const val SUMMARY = "summary"
}

@Composable
fun WearNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.HOME
) {
    val sessionType = SessionStore.type.collectAsState().value

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.HOME) {
            HomeScreen(
                onGoIndoor = { navController.navigate(Routes.INDOOR) },
                onGoOutdoor = { navController.navigate(Routes.OUTDOOR) }
            )
        }

        composable(Routes.INDOOR) {
            val workout = (sessionType as? ActivityType.Indoor)?.workout
            IndoorActivityScreen(
                workout = workout,
                onStopToHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.OUTDOOR) {
            val activity = (sessionType as? ActivityType.Outdoor)?.activity
            OutdoorActivityScreen(
                activity = activity,
                onStopToHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

//        composable(Routes.SUMMARY) {
//            if (summaryState != null) {
//
//            } else {
//                // Show loading while waiting for JSON from phone
//                WearSummaryLoadingScreen(
//                    onCancel = {
//                        navController.navigate(Routes.HOME) {
//                            popUpTo(Routes.HOME) { inclusive = true }
//                        }
//                    }
//                )
//            }
//        }
    }
}