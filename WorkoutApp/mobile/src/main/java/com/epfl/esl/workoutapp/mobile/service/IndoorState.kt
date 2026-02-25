package com.epfl.esl.workoutapp.mobile.service


import com.epfl.esl.workoutapp.mobile.ui.activity.WORKOUT

data class IndoorState(
    val workout: WORKOUT,
    val weightKg: Int,
    val reps: Int,
    val set: Int,
    val isPaused: Boolean,
    val started: Boolean,
)