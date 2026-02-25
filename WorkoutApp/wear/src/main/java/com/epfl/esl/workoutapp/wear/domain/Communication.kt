package com.epfl.esl.workoutapp.wear.domain

data class IndoorStatePacket(
    val workout: IndoorWorkout,
    val weightKg: Int,
    val reps: Int,
    val set: Int,
    val isPaused: Boolean,
    val started: Boolean
)