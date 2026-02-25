package com.epfl.esl.workoutapp.wear.domain

object WearPaths {
    const val COMMAND = "/command"
    const val USER_INFO = "/userInfo"

    // Wear -> mobile
    const val INDOOR_STATE = "/indoor_state"
    const val INDOOR_CMD = "/indoor_cmd"
    const val OUTDOOR_CMD = "/outdoor_cmd" // pause/stop if needed

    // Mobile -> wear
    const val OUTDOOR_METRICS = "/outdoor_metrics"
}