package com.epfl.esl.workoutapp.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.epfl.esl.workoutapp.mobile.ui.activity.WORKOUT
import com.epfl.esl.workoutapp.mobile.ui.activity.WorkoutSummaryScreen
import com.epfl.esl.workoutapp.mobile.ui.activity.IndoorActivityViewModel
import com.epfl.esl.workoutapp.mobile.ui.activity.OutdoorActivityScreen
import com.epfl.esl.workoutapp.mobile.ui.activity.OutdoorActivityViewModel
import com.epfl.esl.workoutapp.mobile.ui.activity.OutdoorSummaryScreen
import com.epfl.esl.workoutapp.mobile.ui.activity.NewActivityScreen
import com.epfl.esl.workoutapp.mobile.ui.activity.PendingWorkoutSession
import com.epfl.esl.workoutapp.mobile.ui.activity.ACTIVITY
import com.epfl.esl.workoutapp.mobile.ui.activity.WorkoutSessionScreen
import com.epfl.esl.workoutapp.mobile.ui.auth.LoginProfileScreen
import com.epfl.esl.workoutapp.mobile.ui.home.HomeScreen
import com.epfl.esl.workoutapp.mobile.ui.home.OutdoorMapDetailScreen
import com.epfl.esl.workoutapp.mobile.ui.profile.ProfileScreen
import com.epfl.esl.workoutapp.mobile.service.WearController
import com.epfl.esl.workoutapp.mobile.service.WearStreamingEffect
import com.google.android.gms.wearable.DataClient
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.epfl.esl.workoutapp.mobile.ui.home.FriendsManageScreen
import com.epfl.esl.workoutapp.mobile.ui.profile.SettingsScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import com.epfl.esl.workoutapp.mobile.ui.activity.WorkoutSessionViewModel
import com.epfl.esl.workoutapp.mobile.ui.activity.WorkoutSet
import com.epfl.esl.workoutapp.mobile.ui.profile.EditProfileScreen
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState




@Composable
fun AppNavGraph(
    navController: NavHostController,
    dataClient: DataClient,
    messageClient: MessageClient,
    wearController: WearController,
    userKey: String,
    onUserKeyChange: (String) -> Unit,
    onBottomBarVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {

        composable("login") {
            LoginProfileScreen(
                onNavigateToHome = { loginInfo ->
                    onBottomBarVisible(true)
                    onUserKeyChange(loginInfo.userKey)
                    navController.navigate("homePage") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                dataClient = dataClient
            )
        }

        composable("homePage") {
            HomeScreen(
                userKey = userKey,
                onOpenFriendsManage = { navController.navigate("friendsManage") },
                onOpenOutdoorMap = { ownerKey, traceRef, _ ->
                    navController.navigate("outdoorMap/$ownerKey/$traceRef")
                }
            )
        }
        composable(
            route = "outdoorMap/{ownerKey}/{traceRef}",
            arguments = listOf(
                navArgument("ownerKey") { type = NavType.StringType },
                navArgument("traceRef") { type = NavType.StringType }
            )
        ) { backStackEntry ->

            val ownerKeyArg = backStackEntry.arguments!!.getString("ownerKey")!!
            val traceRefArg = backStackEntry.arguments!!.getString("traceRef")!!

            OutdoorMapDetailScreen(
                ownerKey = ownerKeyArg,
                traceRef = traceRefArg,
                onBack = { navController.popBackStack() }
            )
        }


        composable("friendsManage") {
            FriendsManageScreen(
                userKey = userKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable("newActivity") {
            NewActivityScreen(
                userKey = userKey,
                onOutdoorSelected = { activity ->
                    wearController.sendSelectOutdoor(activity.name)
                    onBottomBarVisible(true)
                    navController.navigate("outdoorActivity/${activity.name}")
                },
                onIndoorSelected = { workout ->
                    wearController.sendSelectIndoor(workout.name)
                    onBottomBarVisible(true)
                    navController.navigate("indoorActivity/${workout.name}")
                }
            )
        }

        composable(
            route = "indoorActivity/{workout}",
            arguments = listOf(navArgument("workout") { type = NavType.StringType })
        ) { backStackEntry ->

            val workoutName = backStackEntry.arguments!!.getString("workout")!!
            val workout = WORKOUT.valueOf(workoutName)

            // IMPORTANT: scope VM to this back stack entry so it is shared with workout session screen
            val indoorVM: IndoorActivityViewModel = viewModel(backStackEntry)

            // Preselect the workout (so user doesn't have to select again)
            indoorVM.setSelectedWorkout(workout)

            LaunchedEffect(workout) {
                navController.navigate("workout/${workout.name}") {
                    // Optional: avoid piling up identical entries if user re-enters
                    launchSingleTop = true
                }
            }

        }

        composable(
            route = "workout/{workout}",
            arguments = listOf(navArgument("workout") { type = NavType.StringType })
        ) { backStackEntry ->

            val workoutName = backStackEntry.arguments!!.getString("workout")!!
            val workout = WORKOUT.valueOf(workoutName)

            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("indoorActivity/$workoutName")
            }
            val indoorVM: IndoorActivityViewModel = viewModel(parentEntry)
            val sessionVM: WorkoutSessionViewModel = viewModel()

            WearStreamingEffect(
                dataClient = dataClient,
                messageClient = messageClient,
                dataListener = indoorVM,
                msgListener = indoorVM,
                wearController = wearController
            )

            fun finishAndNavigate(sets: List<WorkoutSet>, start: Long, end: Long) {
                indoorVM.setPendingWorkoutSession(
                    PendingWorkoutSession(
                        workout = workout,
                        sets = sets,
                        startTime = start,
                        endTime = end
                    )
                )

                val bestSetReps = sets.maxOfOrNull { it.reps } ?: 0
                val bestSetWeightKg = sets.maxOfOrNull { it.weightKg } ?: 0
                val totalReps = sets.sumOf { it.reps }
                val totalVolume = sets.sumOf { it.reps * it.weightKg }
                val durationSec = ((end - start) / 1000).toInt().coerceAtLeast(0)

                val route =
                    "workoutSummary/${workout.name}/$durationSec/${sets.size}/$bestSetReps/$bestSetWeightKg/$totalReps/$totalVolume"

                navController.navigate(route) {
                    popUpTo("workout/${workout.name}") { inclusive = true }
                    launchSingleTop = true
                }

                wearController.sendShowSummary(
                    type = "indoor",
                    title = workout.name,
                    durationSec = durationSec,
                    totalSets = sets.size,
                    totalReps = totalReps,
//                    avgHr = avgHr,
//                    maxHr = maxHr,
                )
            }

            LaunchedEffect(sessionVM) {
                indoorVM.bindPauseCallback { paused ->
                    Log.w("PAUSE_BIND", "paused=$paused")
                    sessionVM.setTimerPaused(paused)
                }
            }

            LaunchedEffect(Unit) {
                indoorVM.events.collect { event ->
                    if (event is IndoorActivityViewModel.IndoorEvent.Stop) {
                        sessionVM.finishWorkout()
                        val (sets, start, end) = sessionVM.buildSummary()
                        finishAndNavigate(sets, start, end)
                    }
                }
            }

            WorkoutSessionScreen(
                dataClient = dataClient,
                workout = workout,
                indoorActivityViewModel = indoorVM,
                    sessionViewModel = sessionVM,
                onFinishWorkout = { sets, start, end ->
                    finishAndNavigate(sets, start, end)
                },
                wearController = wearController
            )
        }



        composable(
            route = "outdoorActivity/{activity}",
            arguments = listOf(navArgument("activity") { type = NavType.StringType })
        ) { backStackEntry ->

            val activityName = backStackEntry.arguments?.getString("activity") ?: ACTIVITY.RUNNING.name
            val activity = runCatching { ACTIVITY.valueOf(activityName) }.getOrDefault(ACTIVITY.RUNNING)
            val outdoorVM: OutdoorActivityViewModel = viewModel(backStackEntry)

            WearStreamingEffect(
                dataClient = dataClient,
                messageClient = messageClient,
                dataListener = outdoorVM,
                msgListener = outdoorVM,
                wearController = wearController
            )

            OutdoorActivityScreen(
                userKey = userKey,
                activity = activity,
                dataClient = dataClient,
                outdoorActivityViewModel = outdoorVM,
                onFinishOutdoor = { activityName, durationSec, distanceMeters, avgHr, maxHr, avgPaceSecPerKm ->
                    val route =
                        "outdoorSummary/$activityName/$durationSec/$distanceMeters/$avgHr/$maxHr/$avgPaceSecPerKm"

                    navController.navigate(route) {
                        popUpTo("outdoorActivity/${activity.name}") { inclusive = false }
                        launchSingleTop = true
                    }

                    wearController.sendShowSummary(
                        type = "outdoor",
                        title = activityName,
                        durationSec = durationSec,
                        distanceMeters = distanceMeters,
                        avgHr = avgHr,
                        maxHr = maxHr,
                        avgPaceSecPerKm = avgPaceSecPerKm
                    )
                }
            )

        }

        composable("profilePage") {
            ProfileScreen(
                userKey,
                {
                    if (userKey.isNotEmpty()) {
                        navController.navigate("editProfilePage/$userKey")
                    } else {
                        Log.e("Navigation", "User key is empty")
                    }
                }
            )
        }

        composable(
            "editProfilePage/{userKey}",
            arguments = listOf(navArgument("userKey") { type = NavType.StringType })
        ) { backStashEntry ->
            val keyArg = backStashEntry.arguments?.getString("userKey") ?: ""
            EditProfileScreen(
                userKey = keyArg,
                dataClient = dataClient,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "workoutSummary/{workout}/{durationSec}/{totalSets}/{bestSetReps}/{bestSetWeightKg}/{totalReps}/{totalVolume}",
            arguments = listOf(
                navArgument("workout") { type = NavType.StringType },
                navArgument("durationSec") { type = NavType.IntType },
                navArgument("totalSets") { type = NavType.IntType },
                navArgument("bestSetReps") { type = NavType.IntType },
                navArgument("bestSetWeightKg") { type = NavType.IntType },
                navArgument("totalReps") { type = NavType.IntType },
                navArgument("totalVolume") { type = NavType.IntType },
            )
        ) { backStackEntry ->

            val context = LocalContext.current

            val workoutName = backStackEntry.arguments!!.getString("workout")!!
            val durationSec = backStackEntry.arguments!!.getInt("durationSec")
            val totalSets = backStackEntry.arguments!!.getInt("totalSets")
            val bestSetReps = backStackEntry.arguments!!.getInt("bestSetReps")
            val bestSetWeightKg = backStackEntry.arguments!!.getInt("bestSetWeightKg")
            val totalReps = backStackEntry.arguments!!.getInt("totalReps")
            val totalVolume = backStackEntry.arguments!!.getInt("totalVolume")

            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("indoorActivity/$workoutName")
            }
            val indoorVM: IndoorActivityViewModel = viewModel(parentEntry)


            WorkoutSummaryScreen(
                workoutName = workoutName,
                durationSec = durationSec,
                totalSets = totalSets,
                bestSetReps = bestSetReps,
                bestSetWeightKg = bestSetWeightKg,
                totalReps = totalReps,
                totalVolume = totalVolume,
                onShare = { shareText ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share workout"))
                },
                onDone = {
                    onBottomBarVisible(true)
                    indoorVM.consumePendingWorkoutSession()?.let { session ->
                        indoorVM.saveWorkoutSession(
                            userKey = userKey,
                            workout = session.workout,
                            sets = session.sets,
                            startTime = session.startTime,
                            endTime = session.endTime
                        )
                    }
                    navController.popBackStack("newActivity", inclusive = false)
                },
                onDiscardSession = {
                    onBottomBarVisible(true)
                    indoorVM.clearPendingWorkoutSession()
                    navController.popBackStack("newActivity", inclusive = false)
                }
            )
        }
        composable(
            route = "outdoorSummary/{activity}/{durationSec}/{distanceMeters}/{avgHr}/{maxHr}/{avgPaceSecPerKm}",
            arguments = listOf(
                navArgument("activity") { type = NavType.StringType },
                navArgument("durationSec") { type = NavType.IntType },
                navArgument("distanceMeters") { type = NavType.IntType },
                navArgument("avgHr") { type = NavType.IntType },
                navArgument("maxHr") { type = NavType.IntType },
                navArgument("avgPaceSecPerKm") { type = NavType.IntType },
            )
        ) { backStackEntry ->
            val context = LocalContext.current

            val activityName = backStackEntry.arguments!!.getString("activity")!!
            val durationSec = backStackEntry.arguments!!.getInt("durationSec")
            val distanceMeters = backStackEntry.arguments!!.getInt("distanceMeters")
            val avgHr = backStackEntry.arguments!!.getInt("avgHr")
            val maxHr = backStackEntry.arguments!!.getInt("maxHr")
            val avgPaceSecPerKm = backStackEntry.arguments!!.getInt("avgPaceSecPerKm")

            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("outdoorActivity/$activityName")
            }
            val outdoorVM: OutdoorActivityViewModel = viewModel(parentEntry)
            val traceLatLng by outdoorVM.traceLatLngLive.observeAsState(initial = emptyList())


            OutdoorSummaryScreen(
                activityName = activityName,
                durationSec = durationSec,
                distanceMeters = distanceMeters,
                avgHeartRate = avgHr,
                maxHeartRate = maxHr,
                avgPaceSecPerKm = avgPaceSecPerKm,
                onShare = { shareText ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share activity"))
                },
                onDone = {
                    onBottomBarVisible(true)

                    val trace = outdoorVM.consumeTracePoints()
                    outdoorVM.consumePendingOutdoorSession()?.let { session ->
                        val activityId = outdoorVM.saveOutdoorSession(
                            userKey = userKey,
                            activity = session.activity,
                            startTime = session.startTime,
                            endTime = session.endTime,
                            distanceMeters = session.distanceMeters,
                            avgHeartRate = session.avgHeartRate,
                            maxHeartRate = session.maxHeartRate,
                            tracePointsCount = trace.size
                        )
                        if (activityId != null) {
                            outdoorVM.saveOutdoorTrace(
                                userKey = userKey,
                                activityId = activityId,
                                points = trace
                            )
                        }
                    }
                    navController.popBackStack("newActivity", inclusive = false)
                },
                onDiscardSession = {
                    onBottomBarVisible(true)
                    outdoorVM.clearPendingOutdoorSession()
                    navController.popBackStack("homePage", inclusive = false)
                },
                traceLatLng = traceLatLng
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    onBottomBarVisible(false)

                    // Clear navigation stack and return to login
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }


    }
}
