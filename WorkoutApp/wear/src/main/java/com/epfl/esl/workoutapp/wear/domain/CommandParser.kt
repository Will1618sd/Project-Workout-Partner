package com.epfl.esl.workoutapp.wear.domain

import com.epfl.esl.workoutapp.wear.domain.ActivityType
import com.epfl.esl.workoutapp.wear.domain.IndoorWorkout
import com.epfl.esl.workoutapp.wear.domain.OutdoorActivity

object CommandParser {

    sealed class Command {
        data class Select(val type: ActivityType) : Command()
        data class Start(val type: ActivityType) : Command()
        data object Stop : Command()
        data object Unknown : Command()
    }

    fun parse(raw: String): Command {
        val s = raw.trim()

        // Stop variants
        if (s.equals("Stop", ignoreCase = true) || s.startsWith("STOP", ignoreCase = true)) {
            return Command.Stop
        }

        val parts = s.split("|").map { it.trim() }
        if (parts.isEmpty()) return Command.Unknown

        val verb = parts.getOrNull(0)?.uppercase() ?: return Command.Unknown
        val kind = parts.getOrNull(1)?.uppercase()
        val name = parts.getOrNull(2)?.uppercase()

        // Start/Select need kind+name
        if (verb != "START" && verb != "SELECT") return Command.Unknown
        if (kind == null || name == null) return Command.Unknown

        val activityType: ActivityType = when (kind) {
            "INDOOR" -> {
                val workout = runCatching { IndoorWorkout.valueOf(name) }.getOrNull()
                    ?: return Command.Unknown
                ActivityType.Indoor(workout)
            }
            "OUTDOOR" -> {
                val activity = runCatching { OutdoorActivity.valueOf(name) }.getOrNull()
                    ?: return Command.Unknown
                ActivityType.Outdoor(activity)
            }
            else -> return Command.Unknown
        }

        return when (verb) {
            "SELECT" -> Command.Select(activityType)
            "START" -> Command.Start(activityType)
            else -> Command.Unknown
        }
    }
}