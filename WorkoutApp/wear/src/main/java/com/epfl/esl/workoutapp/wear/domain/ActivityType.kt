package com.epfl.esl.workoutapp.wear.domain

sealed class ActivityType {
    data class Indoor(val workout: IndoorWorkout) : ActivityType()
    data class Outdoor(val activity: OutdoorActivity) : ActivityType()
}

enum class IndoorWorkout { DEADLIFT, BENCH, SQUAT }
enum class OutdoorActivity { RUNNING, CYCLING, SWIMMING }