package com.epfl.esl.workoutapp.mobile.ui.home

enum class FeedKind { INDOOR, OUTDOOR }


data class RoutePoint(val lat: Double, val lon: Double)

data class FeedItem(
    val ownerKey: String,
    val ownerLabel: String, // "You" or friend key (or cached username later)
    val kind: FeedKind,
    val type: String,
    val workoutType: String?,
    val startTime: Long,
    val durationSec: Int,

    // Outdoor
    val distanceMeters: Int? = null,
    val avgPaceSecPerKm: Int? = null,
    val traceRef: String? = null,
    val tracePoints: Int? = null,

    // Indoor
    val totalSets: Int? = null,
    val totalVolume: Int? = null,
    val bestSetReps: Int? = null,
    val bestSetWeightKg: Int? = null
)
