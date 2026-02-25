package com.epfl.esl.workoutapp.wear.sensor

import android.util.Log
import kotlin.math.sqrt
import kotlin.math.abs
import com.epfl.esl.workoutapp.wear.domain.IndoorWorkout

data class RepDetectionConfig(
    val threshold: Float,
    val minIntervalMs: Long,
    val alpha: Float = 0.2f,
    val hysteresisRatio: Float = 0.65f
)

interface RepDetector {
    fun reset()
    fun update(
        tsMs: Long,
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float
    ): Boolean
}

class GyroRepDetector(
    private val cfg: RepDetectionConfig
) : RepDetector {
    private var filtered = 0f
    private var armed = true
    private var state = 0
    private var lastRepTs = 0L

    override fun reset() {
        filtered = 0f
        armed = true
        state = 0
        lastRepTs = 0L
    }

    /** returns true if a rep is detected for this sample */
    override fun update(tsMs: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float): Boolean {
        val mag = sqrt(gx*gx + gy*gy + gz*gz)
        //Log.w("RepDetector", "Mag: $mag")

        // IIR low-pass smoothing
        filtered = filtered + cfg.alpha * (mag - filtered)

        // simple hysteresis helps prevent chatter
        val high = cfg.threshold
        val low  = cfg.threshold * 0.65f
        //Log.w("RepDetector", "Filtered: $filtered")

        // re-arm once signal goes low again
        if (!armed && filtered < low) armed = true

        if (armed && filtered > high) {
            if (state == 0) {
                state = 1
            } else if (state == 1) {
                if (tsMs - lastRepTs >= cfg.minIntervalMs) {
                    Log.w("RepDetector", "Rep detected")
                    state = 0
                    lastRepTs = tsMs
                    armed = false
                    return true
                }
            }
        }
        return false
    }
}

class AccRepDetector(
    private val cfg: RepDetectionConfig,
    private val gravityAlpha: Float = 0.02f,
    private val useAbs: Boolean = true
) : RepDetector {
    private var gEst = 9.81f
    private var filtered = 0f
    private var armed = true
    private var lastRepTs = 0L

    override fun reset() {
        gEst = 9.81f
        filtered = 0f
        armed = true
        lastRepTs = 0L
    }

    /** returns true if a rep is detected for this sample */
    override fun update(tsMs: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float): Boolean {
        val mag = sqrt(ax*ax + ay*ay + az*az)
        gEst += gravityAlpha * (mag - gEst)
        val hp = mag - gEst
        val x = if (useAbs) abs(hp) else hp
        Log.w("RepDetector", "Mag: $mag")

        // IIR low-pass smoothing
        filtered = filtered + cfg.alpha * (x - filtered)

        // simple hysteresis helps prevent chatter
        val high = cfg.threshold
        val low  = cfg.threshold * 0.65f
        Log.w("RepDetector", "Filtered: $filtered")

        // re-arm once signal goes low again
        if (!armed && filtered < low) armed = true

        if (armed && filtered > high) {
            if (tsMs - lastRepTs >= cfg.minIntervalMs) {
                Log.w("RepDetector", "Rep detected")
                lastRepTs = tsMs
                armed = false
                return true
            }
        }
        return false
    }
}

object RepDetectorFactory {
    fun create(ex: IndoorWorkout): RepDetector {
        return when (ex) {
            // Deadlift
//            WORKOUT.DEADLIFT -> GyroRepDetector(
//                RepDetectionConfig(threshold = 2.2f, minIntervalMs = 200L)
//            )
            IndoorWorkout.DEADLIFT -> AccRepDetector(
                // Axe x
                RepDetectionConfig(threshold = 2.2f, minIntervalMs = 200L),
                gravityAlpha = 0.02f,
                useAbs = true
            )
            // Bench
//            WORKOUT.BENCH -> GyroRepDetector(
//                RepDetectionConfig(threshold = 2.2f, minIntervalMs = 200L)
//            )
            IndoorWorkout.BENCH -> AccRepDetector(
                // Axe x
                RepDetectionConfig(threshold = 2.2f, minIntervalMs = 200L),
                gravityAlpha = 0.02f,
                useAbs = true
            )
            // Squat
//            WORKOUT.SQUAT -> GyroRepDetector(
//                RepDetectionConfig(threshold = 2.2f, minIntervalMs = 200L)
//            )
            IndoorWorkout.SQUAT -> AccRepDetector(
                // Axe y
                RepDetectionConfig(threshold = 2.2f, minIntervalMs = 200L),
                gravityAlpha = 0.02f,
                useAbs = true
            )
        }
    }
}