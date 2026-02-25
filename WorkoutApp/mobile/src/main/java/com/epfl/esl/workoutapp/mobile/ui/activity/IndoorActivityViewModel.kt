package com.epfl.esl.workoutapp.mobile.ui.activity

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.yml.charts.common.model.Point
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Transaction
import com.google.firebase.database.MutableData
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.epfl.esl.workoutapp.mobile.data.model.ImuSample
import com.epfl.esl.workoutapp.mobile.service.IndoorState
import com.epfl.esl.workoutapp.mobile.service.WearPaths
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.epfl.esl.workoutapp.mobile.logic.processExerciseSet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

enum class WORKOUT(val label: String) {
    DEADLIFT("Deadlift"),
    BENCH("Bench Press"),
    SQUAT("Squat")
}

data class WorkoutSet(
    val reps: Int = 0,
    val weightKg: Int = 0
)

data class WorkoutSession(
    val workoutType: String = "",
    val durationSec: Int = 0,
    val totalSets: Int = 0,
    val bestSetReps: Int = 0,
    val bestSetWeightKg: Int = 0,
    val totalReps: Int = 0,
    val totalVolume: Int = 0,
    val sets: Map<String, WorkoutSet> = emptyMap()
)

data class WorkoutAggBucket(
    val sessions: Int = 0,
    val activeSec: Int = 0,
    val totalReps: Int = 0,
    val totalVolume: Int = 0,
    val updatedAt: Long = 0L
)

data class WorkoutBests(
    val bestSetReps: Int = 0,
    val bestSetWeightKg: Int = 0,
    val bestSessionVolume: Int = 0,
    val longestSessionSec: Int = 0,
    val updatedAt: Long = 0L
)

data class PendingWorkoutSession(
    val workout: WORKOUT,
    val sets: List<WorkoutSet>,
    val startTime: Long,
    val endTime: Long
)

class IndoorActivityViewModel(application: Application) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    val profileRef: DatabaseReference = database.getReference("Profiles")

    // HR data
    private val _heartRate = MutableLiveData<Int>(0)
    val heartRate: LiveData<Int>
        get() = _heartRate
    private val _heartRateList = ArrayList<Point>()
    private val _heartRateListLiveData = MutableLiveData<List<Point>>()
    val heartRateList: LiveData<List<Point>>
        get() = _heartRateListLiveData

    private var pendingWorkoutSession: PendingWorkoutSession? = null

    fun setPendingWorkoutSession(session: PendingWorkoutSession) {
        pendingWorkoutSession = session
    }

    fun consumePendingWorkoutSession(): PendingWorkoutSession? {
        val s = pendingWorkoutSession
        pendingWorkoutSession = null
        return s
    }

    fun clearPendingWorkoutSession() {
        pendingWorkoutSession = null
    }

    // --- state that wear overwrites on mobile --
    private val _indoorState = MutableStateFlow(
        IndoorState(
            workout = WORKOUT.DEADLIFT,
            weightKg = 60,
            reps = 0,
            set = 1,
            isPaused = false,
            started = false
        )
    )
    val indoorState = _indoorState.asStateFlow()


    // --- events that UI must react to (Stop triggers onFinishWorkout) ---
    sealed class IndoorEvent {
        data object Start : IndoorEvent()
        data object Pause : IndoorEvent()
        data object Resume : IndoorEvent()
        data class Stop(val workout: WORKOUT) : IndoorEvent()
    }

    private val _events = MutableSharedFlow<IndoorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun setPausedOnMobile(paused: Boolean) {
        pauseCallback?.invoke(paused)
        Log.w("M_INDOOR", "Mobile paused=$paused")
    }

    // IMU data
    private val _latestImu = MutableLiveData(
        ImuSample(
            timestampMs = 0L,
            accX = 0f, accY = 0f, accZ = 0f,
            gyroX = 0f, gyroY = 0f, gyroZ = 0f
        )
    )
    val latestImu: LiveData<ImuSample> = _latestImu

    private val _selectedWorkout = MutableLiveData<WORKOUT?>(WORKOUT.DEADLIFT)
    val selectedWorkout: LiveData<WORKOUT?> = _selectedWorkout
    fun setSelectedWorkout(workout: WORKOUT) {
        _selectedWorkout.value = workout
    }

    data class PredictionResult(val label: String, val reps: Int)
    private val _prediction = MutableSharedFlow<PredictionResult>(extraBufferCapacity = 1)
    val prediction = _prediction.asSharedFlow()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach( { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (uri.path) {
                    "/heart_rate" -> {
                        handleHeartRate(event)
                    }
                    "/imu_batch" -> {
                        handleImuBatch(event)
                    }
                    WearPaths.INDOOR_STATE -> {
                        val map = DataMapItem.fromDataItem(event.dataItem).dataMap

                        val workoutName = map.getString("workout") ?: return@forEach
                        val workout = runCatching { WORKOUT.valueOf(workoutName) }.getOrNull()
                            ?: return@forEach

                        val weightKg = map.getInt("weightKg")
                        val reps = map.getInt("reps")
                        val set = map.getInt("set")
                        val isPaused = map.getBoolean("isPaused")
                        val started = map.getBoolean("started")

                        _indoorState.value = IndoorState(
                            workout = workout,
                            weightKg = weightKg,
                            reps = reps,
                            set = set,
                            isPaused = isPaused,
                            started = started
                        )

                        // Pause affects timer/algo on mobile immediately
                        setPausedOnMobile(isPaused)

                        Log.d("M_INDOOR", "rx indoor_state $workout w=$weightKg r=$reps s=$set paused=$isPaused")
                    } else -> {
                        Log.d("IndoorVM", "Unknown data: ${uri}")
                    }
                }
            }
        })
    }

    // ------------------ MessageClient (instant commands) ------------------

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.INDOOR_CMD && event.path != "/workout_data") return
//        if (event.path == WearPaths.INDOOR_CMD) {

        val msg = String(event.data)
        Log.w("M_INDOOR_CMD", "rx '$msg'")

        when {
            msg.equals("PAUSE", ignoreCase = true) -> {
                setPausedOnMobile(true)
                _events.tryEmit(IndoorEvent.Pause)
            }

            msg.equals("RESUME", ignoreCase = true) -> {
                setPausedOnMobile(false)
                _events.tryEmit(IndoorEvent.Resume)
            }

            msg.startsWith("STOP|", ignoreCase = true) -> {
                val parts = msg.split("|")
                val workoutName = parts.getOrNull(1) ?: return
                val workout = runCatching { WORKOUT.valueOf(workoutName) }.getOrNull() ?: return

                // stop timer/algo immediately
                setPausedOnMobile(true)

                _events.tryEmit(IndoorEvent.Stop(workout))
            }

            else -> Log.w("M_INDOOR_CMD", "Unknown cmd '$msg'")
        }
        if (event.path == "/workout_data") {
            Log.w("MobileProcessing", "Received workout data")
            viewModelScope.launch(Dispatchers.Default) { // Heavy lifting on Default Dispatcher
                try {
                    // A. Deserialize
                    val jsonString = String(event.data, Charsets.UTF_8)
                    val gson = Gson()
                    val type = object : TypeToken<List<ImuSample>>() {}.type
                    val rawData: List<ImuSample> = gson.fromJson(jsonString, type)

                    Log.w("MobileProcessing", "Received ${rawData.size} samples. Processing...")

                    // B. Process (ONNX + Signal Processing)
                    // We reuse the function logic you wrote in SignalProcessing.kt
                    // Ensure processExerciseSet returns a Pair/DataClass instead of just printing
                    val (label, reps) = processExerciseSet(getApplication(), rawData)

                    Log.w("MobileProcessing", "Result: $label, $reps reps")

                    // C. Emit Result
                    _prediction.tryEmit(PredictionResult(label, reps))

                    // Optional: Auto-update stats if confident?
                    // Usually better to let UI decide.

                } catch (e: Exception) {
                    Log.e("MobileProcessing", "Error processing set", e)
                }

            }
        }

    }

    fun sendCommandToWear(command: String, context: Context) {
        Thread {
            try {
                val connectedNodes = Tasks
                    .await(Wearable.getNodeClient(context).connectedNodes)
                    .map { it.id }

                val messageClient = Wearable.getMessageClient(context)

                connectedNodes.forEach { nodeId ->
                    Tasks.await(
                        messageClient.sendMessage(
                            nodeId,
                            WearPaths.INDOOR_CMD,
                            command.toByteArray()
                        )
                    )
                    Log.w("WearCmd", "Sent '$command' to $nodeId")
                }
            } catch (e: Exception) {
                Log.e("WearCmd", "Failed to send '$command'", e)
            }
        }.start()
    }

    fun saveWorkoutSession(
        userKey: String,
        workout: WORKOUT,
        sets: List<WorkoutSet>,
        startTime: Long,
        endTime: Long
    ) {
        val indoorRootRef = profileRef.child(userKey).child("Activities").child("IndoorActivity")
        val activityId = indoorRootRef.push().key ?: return
        val activityRef = indoorRootRef.child(activityId)

        val bestSetReps = sets.maxOfOrNull { it.reps } ?: 0
        val bestSetWeight = sets.maxOfOrNull { it.weightKg } ?: 0
        val totalReps = sets.sumOf { it.reps }
        val totalVolume = sets.sumOf { it.reps * it.weightKg }
        val durationSec = ((endTime - startTime) / 1000).toInt()

        val dKey = dayKey(startTime)
        val wKey = weekKey(startTime)
        val mKey = monthKey(startTime)

        val setsMap = sets.mapIndexed { idx, s -> idx.toString() to s }.toMap()

        val indoorActivity = WorkoutSession(
            workoutType = workout.name,
            durationSec = durationSec,
            totalSets = sets.size,
            bestSetReps = bestSetReps,
            bestSetWeightKg = bestSetWeight,
            totalReps = totalReps,
            totalVolume = totalVolume,
            sets = setsMap
        )

        val envelope = mapOf(
            "type" to "WORKOUT",
            "startTime" to startTime,
            "endTime" to endTime,
            "durationSec" to durationSec,
            "dayKey" to dKey,
            "weekKey" to wKey,
            "monthKey" to mKey
        )

        val updates = HashMap<String, Any>()
        envelope.forEach { (k, v) -> updates[k] = v as Any }

        updates["workoutType"] = indoorActivity.workoutType
        updates["durationSec"] = indoorActivity.durationSec
        updates["totalSets"] = indoorActivity.totalSets
        updates["bestSetReps"] = indoorActivity.bestSetReps
        updates["bestSetWeightKg"] = indoorActivity.bestSetWeightKg
        updates["totalReps"] = indoorActivity.totalReps
        updates["totalVolume"] = indoorActivity.totalVolume
        updates["sets"] = indoorActivity.sets

        activityRef.updateChildren(updates)
            .addOnSuccessListener {
                updateIndoorStats(
                    userKey = userKey,
                    workout = workout,
                    dayKey = dKey,
                    weekKey = wKey,
                    monthKey = mKey,
                    durationSec = durationSec,
                    totalReps = totalReps,
                    totalVolume = totalVolume,
                    bestSetReps = bestSetReps,
                    bestSetWeightKg = bestSetWeight
                )
            }
    }
    private fun updateAggBucket(
        bucketRef: DatabaseReference,
        durationSec: Int,
        totalReps: Int,
        totalVolume: Int
    ) {
        bucketRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(WorkoutAggBucket::class.java) ?: WorkoutAggBucket()

                val updated = current.copy(
                    sessions = current.sessions + 1,
                    activeSec = current.activeSec + durationSec,
                    totalReps = current.totalReps + totalReps,
                    totalVolume = current.totalVolume + totalVolume,
                    updatedAt = System.currentTimeMillis()
                )

                currentData.value = updated
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                val refString = bucketRef.toString()

                if (error != null) {
                    Log.e("Stats", "Agg transaction failed at $refString", error.toException())
                } else if (!committed) {
                    Log.w("Stats", "Agg transaction NOT committed at $refString")
                } else {
                    Log.d("Stats", "Agg updated at $refString")
                }
            }
        })
    }
    private fun updateBests(
        bestsRef: DatabaseReference,
        bestSetReps: Int,
        bestSetWeightKg: Int,
        sessionVolume: Int,
        durationSec: Int
    ) {
        bestsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(WorkoutBests::class.java) ?: WorkoutBests()

                val updated = current.copy(
                    bestSetReps = maxOf(current.bestSetReps, bestSetReps),
                    bestSetWeightKg = maxOf(current.bestSetWeightKg, bestSetWeightKg),
                    bestSessionVolume = maxOf(current.bestSessionVolume, sessionVolume),
                    longestSessionSec = maxOf(current.longestSessionSec, durationSec),
                    updatedAt = System.currentTimeMillis()
                )

                currentData.value = updated
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                val refString = bestsRef.toString()

                if (error != null) {
                    Log.e("Stats", "Bests transaction failed at $refString", error.toException())
                } else if (!committed) {
                    Log.w("Stats", "Bests transaction NOT committed at $refString")
                } else {
                    Log.d("Stats", "Bests updated at $refString")
                }
            }
        })
    }
    private fun updateIndoorStats(
        userKey: String,
        workout: WORKOUT,
        dayKey: String,
        weekKey: String,
        monthKey: String,
        durationSec: Int,
        totalReps: Int,
        totalVolume: Int,
        bestSetReps: Int,
        bestSetWeightKg: Int
    ) {
        val statsRoot = profileRef.child(userKey).child("Stats")

        // ---- overall ----
        val overallRoot = statsRoot.child("INDOOR").child("overall")
        updateAggBucket(overallRoot.child("daily").child(dayKey), durationSec, totalReps, totalVolume)
        updateAggBucket(overallRoot.child("weekly").child(weekKey), durationSec, totalReps, totalVolume)
        updateAggBucket(overallRoot.child("monthly").child(monthKey), durationSec, totalReps, totalVolume)
        updateBests(overallRoot.child("bests"), bestSetReps, bestSetWeightKg, totalVolume, durationSec)

        // ---- ALL rollup  ----
        val allRoot = statsRoot.child("ALL")
        updateAggBucket(allRoot.child("daily").child(dayKey), durationSec, totalReps, totalVolume)
        updateAggBucket(allRoot.child("weekly").child(weekKey), durationSec, totalReps, totalVolume)
        updateAggBucket(allRoot.child("monthly").child(monthKey), durationSec, totalReps, totalVolume)


        // ---- bySubtype ----
        val subtypeRoot = statsRoot.child("INDOOR").child("bySubtype").child(workout.name)
        updateAggBucket(subtypeRoot.child("daily").child(dayKey), durationSec, totalReps, totalVolume)
        updateAggBucket(subtypeRoot.child("weekly").child(weekKey), durationSec, totalReps, totalVolume)
        updateAggBucket(subtypeRoot.child("monthly").child(monthKey), durationSec, totalReps, totalVolume)
        updateBests(subtypeRoot.child("bests"), bestSetReps, bestSetWeightKg, totalVolume, durationSec)
    }

    private fun handleStats(event: DataEvent) {
        val map = DataMapItem.fromDataItem(event.dataItem).dataMap
        val hr = map.getInt("heart_rate")
        val reps = map.getInt("reps")

        Log.d("IndoorVM", "Received Stats -> HR: $hr, Reps: $reps")
        _heartRate.postValue(hr)
    }

    private fun handleHeartRate(event: DataEvent) {
        val newValues = DataMapItem.fromDataItem(event.dataItem)
            .dataMap.getIntegerArrayList("HEART_RATE")

        newValues?.let { list ->
            var nextIndex = _heartRateList.size
            list.forEach { hr ->
                _heartRateList.add(Point(nextIndex.toFloat(), hr.toFloat()))
                nextIndex += 1
            }
            val lastElementIndex = _heartRateList.lastIndex
            _heartRate.postValue(_heartRateList[lastElementIndex].y.toInt())
            _heartRateListLiveData.postValue(_heartRateList)
        }
        Log.d("IndoorVM", "Received HR -> $newValues")
    }

    private fun handleImuBatch(event: DataEvent) {
        val map = DataMapItem.fromDataItem(event.dataItem).dataMap

        val ts = map.getLongArray("ts") ?: return
        val accX = map.getFloatArray("accX") ?: return
        val accY = map.getFloatArray("accY") ?: return
        val accZ = map.getFloatArray("accZ") ?: return
        val gyroX = map.getFloatArray("gyroX") ?: return
        val gyroY = map.getFloatArray("gyroY") ?: return
        val gyroZ = map.getFloatArray("gyroZ") ?: return

        if (ts.isEmpty()) return

        // Take the most recent sample for debug UI
        val i = ts.lastIndex
        _latestImu.postValue(
            ImuSample(
                timestampMs = ts[i],
                accX = accX[i], accY = accY[i], accZ = accZ[i],
                gyroX = gyroX[i], gyroY = gyroY[i], gyroZ = gyroZ[i]
            )
        )
        Log.d("IndoorVM", "Received IMU -> $i")
    }
    private var pauseCallback: ((Boolean) -> Unit)? = null

    fun bindPauseCallback(cb: (Boolean) -> Unit) {
        pauseCallback = cb
    }
}

class WorkoutSessionViewModel : ViewModel() {

    private val _reps = MutableLiveData(0)
    val reps: LiveData<Int> = _reps

    private val _sets = MutableLiveData(1)
    val sets: LiveData<Int> = _sets

    private val _time = MutableLiveData("00:00")
    val time: LiveData<String> = _time

    // New: weight input for the current set
    private val _weightKg = MutableLiveData(0)
    val weightKg: LiveData<Int> = _weightKg

    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L

    private var elapsedMs: Long = 0L
    private var isTimerPaused: Boolean = false

    private var hasStarted = false

    // New: store completed sets
    private val completedSets = mutableListOf<WorkoutSet>()

    fun startSession(workout: WORKOUT) {
        if (hasStarted) return
        hasStarted = true
        completedSets.clear()
        _reps.value = 0
        _sets.value = 1

        elapsedMs = 0L
        isTimerPaused = false
        startTimeMs = System.currentTimeMillis()

        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(200) // smoother than 1s; OK to set back to 1000 if you prefer

                if (!isTimerPaused) {
                    val now = System.currentTimeMillis()
                    val totalMs = elapsedMs + (now - startTimeMs)
                    _time.postValue(formatTimeMs(totalMs))
                }
            }
        }
    }

    fun setTimerPaused(paused: Boolean) {
        if (paused == isTimerPaused) return

        val now = System.currentTimeMillis()

        if (paused) {
            // accumulate time up to now
            elapsedMs += (now - startTimeMs)
        } else {
            // resume counting from now
            startTimeMs = now
        }

        isTimerPaused = paused

        // update UI immediately
        _time.postValue(formatTimeMs(elapsedMs))
    }

    private fun formatTimeMs(totalMs: Long): String {
        val totalSec = (totalMs / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return "%02d:%02d".format(m, s)
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    fun setWeightKg(weight: Int) {
        _weightKg.value = weight.coerceAtLeast(0)
    }

    fun setReps(reps: Int) {
        _reps.value = reps.coerceAtLeast(0)
    }

    fun setSet(set: Int) {
        _sets.value = set.coerceAtLeast(1)
    }

    fun addRep() {
        _reps.value = (_reps.value ?: 0) + 1
    }

    /**
     * End the current set and start a new one.
     * Saves the set with current reps + current weight.
     */
    fun nextSet() {
        // Save current set
        val repsNow = _reps.value ?: 0
        val weightNow = _weightKg.value ?: 0
        completedSets.add(WorkoutSet(reps = repsNow, weightKg = weightNow))

        // Prepare next set
        _sets.value = (_sets.value ?: 1) + 1
        _reps.value = 0
    }

    fun finishWorkout() {
        if (!isTimerPaused) {
            val now = System.currentTimeMillis()
            elapsedMs += (now - startTimeMs)
        }
        isTimerPaused = true
        timerJob?.cancel()
        timerJob = null

        hasStarted = false
    }

    /**
     * Build the sets list + start/end timestamps for saving to Firebase.
     * Call this when finishing. It will also include the *current* set
     * (even if user didn't press "Next Set").
     */
    fun buildSummary(): Triple<List<WorkoutSet>, Long, Long> {
        val endTimeMs = System.currentTimeMillis()

        val setsToSave = completedSets.toMutableList()

        // Include current set if it has reps (or if you want to always include it)
        val repsNow = _reps.value ?: 0
        val weightNow = _weightKg.value ?: 0
        if (repsNow > 0 || setsToSave.isEmpty()) {
            setsToSave.add(WorkoutSet(reps = repsNow, weightKg = weightNow))
        }

        return Triple(setsToSave, startTimeMs, endTimeMs)
    }
}

private fun dayKey(timeMs: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(timeMs)

private fun weekKey(timeMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return "%04d-W%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.WEEK_OF_YEAR)
    )
}

private fun monthKey(timeMs: Long): String =
    SimpleDateFormat("yyyyMM", Locale.US).format(timeMs)

