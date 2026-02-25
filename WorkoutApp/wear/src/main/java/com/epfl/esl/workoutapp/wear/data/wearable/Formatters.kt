package com.epfl.esl.workoutapp.wear.data.wearable

object Formatters {

    fun timeMmSs(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        return String.format("%02d:%02d", s / 60, s % 60)
    }

    fun speedKphText(speedKph: Float): String =
        String.format("%.1f km/h", speedKph.coerceAtLeast(0f))

    fun distanceText(distanceM: Int): String {
        val m = distanceM.coerceAtLeast(0)
        return if (m >= 1000)
            String.format("%.2f km", m / 1000f)
        else
            "$m m"
    }
}