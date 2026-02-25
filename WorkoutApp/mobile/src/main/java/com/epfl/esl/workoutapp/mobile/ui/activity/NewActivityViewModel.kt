package com.epfl.esl.workoutapp.mobile.ui.activity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.epfl.esl.workoutapp.mobile.ui.activity.WORKOUT


enum class ActivityCategory { INDOOR, OUTDOOR }

sealed class NewActivityNavEvent {
    data class GoOutdoor(val activity: ACTIVITY) : NewActivityNavEvent()
    data class GoIndoor(val workout: WORKOUT) : NewActivityNavEvent()
}

enum class ACTIVITY(val activity: String, val imageResource: ImageVector) {
    RUNNING("Running", Icons.AutoMirrored.Filled.DirectionsRun),
    CYCLING("Cycling", Icons.AutoMirrored.Filled.DirectionsBike),
    SWIMMING("Swimming", Icons.Default.Pool);

    fun isOutdoor(): Boolean {
        return this == RUNNING || this == CYCLING || this == SWIMMING
    }
}

class NewActivityViewModel : ViewModel() {

    private val _category = MutableLiveData<ActivityCategory?>(null)
    val category: LiveData<ActivityCategory?> get() = _category

    private val _selectedOutdoor = MutableLiveData<ACTIVITY?>(null)
    val selectedOutdoor: LiveData<ACTIVITY?> get() = _selectedOutdoor

    private val _selectedIndoor = MutableLiveData<WORKOUT?>(null)
    val selectedIndoor: LiveData<WORKOUT?> get() = _selectedIndoor

    // One-time navigation event
    private val _navEvent = MutableLiveData<NewActivityNavEvent?>(null)
    val navEvent: LiveData<NewActivityNavEvent?> get() = _navEvent

    fun selectCategory(category: ActivityCategory) {
        _category.value = category
        // Reset second-step selection when category changes
        _selectedOutdoor.value = null
        _selectedIndoor.value = null
        _navEvent.value = null
    }

    fun resetCategory() {
        _category.value = null
        _selectedOutdoor.value = null
        _selectedIndoor.value = null
        _navEvent.value = null
    }

    fun pickOutdoor(activity: ACTIVITY) {
        // Only accept outdoor activities
        if (!activity.isOutdoor()) return
        _selectedOutdoor.value = activity
        _navEvent.value = NewActivityNavEvent.GoOutdoor(activity)
    }

    fun pickIndoor(workout: WORKOUT) {
        _selectedIndoor.value = workout
        _navEvent.value = NewActivityNavEvent.GoIndoor(workout)
    }

    // Call after navigation so the event is not re-fired on recomposition
    fun consumeNavEvent() {
        _navEvent.value = null
    }
}
