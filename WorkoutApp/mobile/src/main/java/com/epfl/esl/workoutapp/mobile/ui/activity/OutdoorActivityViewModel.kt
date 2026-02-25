package com.epfl.esl.workoutapp.mobile.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import co.yml.charts.common.model.Point
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.epfl.esl.workoutapp.mobile.data.model.ImuSample
import com.epfl.esl.workoutapp.mobile.logic.processExerciseSet
import com.epfl.esl.workoutapp.mobile.service.OutdoorState
import com.epfl.esl.workoutapp.mobile.service.WearPaths
import com.epfl.esl.workoutapp.mobile.ui.activity.IndoorActivityViewModel.IndoorEvent
import com.epfl.esl.workoutapp.mobile.ui.activity.IndoorActivityViewModel.PredictionResult
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await


data class LocationData(val latitude: Double,
                        val longitude: Double,
                        val description: String)
data class OutdoorActivitySummary(
    val distanceMeters: Int = 0,
    val durationSec: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgPaceSecPerKm: Int = 0
)

data class OutdoorAggBucket(
    val sessions: Int = 0,
    val activeSec: Int = 0,
    val distanceMeters: Int = 0,
    val updatedAt: Long = 0L
)

data class OutdoorBests(
    val fastestAvgPaceSecPerKm: Int = 0, // lower is better, 0 means unset
    val longestDistanceMeters: Int = 0,
    val longestSessionSec: Int = 0,
    val updatedAt: Long = 0L
)

data class PendingOutdoorSession(
    val activity: ACTIVITY,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Int,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
)

data class TracePoint(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val t: Long = 0L,        // epoch ms
    val acc: Float = 0f      // meters
)

enum class OutdoorSessionState { IDLE, RUNNING, PAUSED }

data class OutdoorUiState(
    val timeSec: Int = 0,
    val distanceM: Int = 0,
    val speedKph: Float = 0f,
    val started: Boolean = false,
    val isPaused: Boolean = false
)

class OutdoorActivityViewModel(application: Application) :
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

    // Location
    private val _locationData = MutableLiveData<LocationData>(
        LocationData(
            46.5197,
            6.6323,
            "Lausanne"
        )
    ) //Lausanne as an initial location
    val locationData: LiveData<LocationData>
        get() = _locationData

    private val _sessionState = MutableLiveData(OutdoorSessionState.IDLE)
    val sessionState: LiveData<OutdoorSessionState> get() = _sessionState

    private val _elapsedSec = MutableLiveData(0)
    val elapsedSec: LiveData<Int> get() = _elapsedSec

    private val _distanceMetersLive = MutableLiveData(0)
    val distanceMetersLive: LiveData<Int> get() = _distanceMetersLive

    private val _speedMpsLive = MutableLiveData(0f)
    val speedMpsLive: LiveData<Float> get() = _speedMpsLive

    private var startTimeMs: Long? = null
    private var accumulatedPauseMs: Long = 0L
    private var pauseStartedMs: Long? = null

    private var lastLocation: android.location.Location? = null

    private var locationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    // For measuring update cadence and diagnosing batching
    private var lastFixElapsedNanos: Long? = null


    private var pendingOutdoorSession: PendingOutdoorSession? = null

    fun setPendingOutdoorSession(session: PendingOutdoorSession) {
        pendingOutdoorSession = session
    }

    fun consumePendingOutdoorSession(): PendingOutdoorSession? {
        val s = pendingOutdoorSession
        pendingOutdoorSession = null
        return s
    }

    fun clearPendingOutdoorSession() {
        pendingOutdoorSession = null
    }

    private var timerJob: Job? = null

    //TEST
    private fun caller(): String =
        Throwable().stackTrace
            .firstOrNull { it.className.contains("workoutapp") }
            ?.let { "${it.fileName}:${it.lineNumber} ${it.methodName}" }
            ?: "unknown"

    private var startUpdatesSeq = 0

    // --- events that UI must react to (Stop triggers onFinishWorkout) ---
    sealed class OutdoorEvent {
        data object Start : OutdoorEvent()
        data object Pause : OutdoorEvent()
        data class Stop(val activity: ACTIVITY) : OutdoorEvent()
    }

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<OutdoorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // Trace (polyline)
    private val _traceLatLngLive = MutableLiveData<List<LatLng>>(emptyList())
    val traceLatLngLive: LiveData<List<LatLng>> get() = _traceLatLngLive

    private val tracePoints = ArrayList<TracePoint>()   // DB-ready points
    private var lastStoredTraceLocation: android.location.Location? = null
    private var lastStoredTraceTimeMs: Long? = null

    private companion object {
        const val GPS_TAG = "OutdoorGPS"

        // Trace sampling knobs (tune later if needed)
        const val TRACE_MAX_ACC_METERS = 25f
        const val TRACE_MIN_DISTANCE_METERS = 3f
        const val TRACE_MAX_INTERVAL_MS = 2_000L
    }

    private var metricsTxJob: Job? = null
    private var lastSent: String? = null

    // ------------------ MessageClient (instant commands) ------------------
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.OUTDOOR_CMD) return

        val msg = String(event.data)
        Log.w("M_OUTDOOR_CMD", "rx '$msg'")

        when {
            msg.startsWith("START|", ignoreCase = true) -> {
                Log.w("M_OUTDOOR_CMD", "START")
                startOutdoorSession(getApplication())
            }

            msg.equals("PAUSE", ignoreCase = true) -> {
                Log.w("M_OUTDOOR_CMD", "PAUSE")
                pauseOutdoorSession()
            }

            msg.equals("RESUME", ignoreCase = true) -> {
                Log.w("M_OUTDOOR_CMD", "RESUME")
                resumeOutdoorSession(getApplication())
            }

            msg.startsWith("STOP|", ignoreCase = true) -> {
                Log.w("M_OUTDOOR_CMD", "STOP")

                // If wear sends activity name, parse it. Otherwise default to current activity shown.
                val parts = msg.split("|")
                val activityName = parts.getOrNull(1)
                val activity = runCatching { ACTIVITY.valueOf(activityName ?: "") }.getOrNull()
                    ?: ACTIVITY.RUNNING

                _events.tryEmit(OutdoorEvent.Stop(activity))
            }

            else -> {
                Log.w("M_OUTDOOR_CMD", "Unknown cmd '$msg'")
            }
        }
    }
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED &&
                    it.dataItem.uri.path == "/heart_rate" }
            .forEach { event ->
                val newValue = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.getIntegerArrayList("HEART_RATE")
                newValue?.let {
                    var nextIndex = _heartRateList.size
                    it.forEach { i ->
                        _heartRateList
                            .add(Point(nextIndex.toFloat(), i.toFloat()))
                        nextIndex += 1
                    }
                    val lastElementIndex = _heartRateList.lastIndex
                    _heartRate.value = _heartRateList
                        .get(lastElementIndex).y.toInt()
                    _heartRateListLiveData.value = _heartRateList
                }
                Log.w("OutdoorVM", "HR -> ${newValue}")
            }
    }

    fun sendCommandToWear(command: String, context: Context) {
        Thread( Runnable {
            try {
                val connectedNodes = Tasks.await(
                    Wearable.getNodeClient(context).connectedNodes
                ).map { it.id }

                if (connectedNodes.isEmpty()) {
                    Log.w("WearCmd", "No connected Wear nodes. '$command' not sent.")
                }
                else {
                    connectedNodes.forEach {
                        val messageClient: MessageClient = Wearable.getMessageClient(context)
                        messageClient.sendMessage(it, "/command", command.toByteArray())
                    }
                }
            } catch (e: Exception) {
                Log.e("WearCmd", "Failed to send command '$command'", e)
            }
        }).start()
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(context: Context) {
        val locationProvider =
            LocationServices.getFusedLocationProviderClient(context)
        Log.w(GPS_TAG, "getLastLocation(): requesting last known location")

        locationProvider.lastLocation.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val location = task.result
                val address = getAddress(
                    context, location.latitude,
                    location.longitude
                )
                val locationData = LocationData(
                    location.latitude,
                    location.longitude, address
                )
                _locationData.postValue(locationData)
                val ageSec = (System.currentTimeMillis() - location.time) / 1000.0
                Log.w(
                    GPS_TAG,
                    "getLastLocation(): lat=${location.latitude}, lon=${location.longitude}, " +
                            "acc=${location.accuracy}m, age=${"%.1f".format(ageSec)}s, provider=${location.provider}"
                )

            }else{
                Log.w(GPS_TAG, "getLastLocation(): no last known location (taskSuccess=${task.isSuccessful})")

            }
        }
    }

    private fun getAddress(context: Context, latitude: Double, longitude: Double): String {
        var address = ""
        val gcd = Geocoder(context, Locale.getDefault())
        val addresses: List<Address>
        try {
            addresses = gcd.getFromLocation(
                latitude,
                longitude,
                1
            ) as List<Address>
            if (addresses.isNotEmpty()) {
                address = addresses[0].getAddressLine(0)
            }
        } catch (e: IOException) {
            Log.e(OutdoorActivityViewModel::class.java.simpleName, "${e.message}")
        }
        return address
    }

    private fun updateOutdoorAggBucket(
        bucketRef: DatabaseReference,
        durationSec: Int,
        distanceMeters: Int,
    ) {
        bucketRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(OutdoorAggBucket::class.java) ?: OutdoorAggBucket()

                val updated = current.copy(
                    sessions = current.sessions + 1,
                    activeSec = current.activeSec + durationSec,
                    distanceMeters = current.distanceMeters + distanceMeters,
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
                    Log.e("Stats", "Outdoor agg transaction failed at $refString", error.toException())
                } else if (!committed) {
                    Log.w("Stats", "Outdoor agg transaction NOT committed at $refString")
                } else {
                    Log.w("Stats", "Outdoor agg updated at $refString")
                }
            }
        })
    }
    private fun updateOutdoorBests(
        bestsRef: DatabaseReference,
        avgPaceSecPerKm: Int,
        distanceMeters: Int,
        durationSec: Int,
    ) {
        bestsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(OutdoorBests::class.java) ?: OutdoorBests()

                val fastest = when {
                    avgPaceSecPerKm <= 0 -> current.fastestAvgPaceSecPerKm
                    current.fastestAvgPaceSecPerKm <= 0 -> avgPaceSecPerKm
                    else -> minOf(current.fastestAvgPaceSecPerKm, avgPaceSecPerKm)
                }

                val updated = current.copy(
                    fastestAvgPaceSecPerKm = fastest,
                    longestDistanceMeters = maxOf(current.longestDistanceMeters, distanceMeters),
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
                    Log.e("Stats", "Outdoor bests transaction failed at $refString", error.toException())
                } else if (!committed) {
                    Log.w("Stats", "Outdoor bests transaction NOT committed at $refString")
                } else {
                    Log.w("Stats", "Outdoor bests updated at $refString")
                }
            }
        })
    }
    private fun updateOutdoorStats(
        userKey: String,
        activity: ACTIVITY,          // RUNNING/CYCLING/SWIMMING
        dayKey: String,
        weekKey: String,
        monthKey: String,
        durationSec: Int,
        distanceMeters: Int,
        avgPaceSecPerKm: Int
    ) {
        val statsRoot = profileRef.child(userKey).child("Stats")

        // ---- type-specific (RUNNING/CYCLING/SWIMMING) ----
        val typeRoot = statsRoot.child(activity.name)

        updateOutdoorAggBucket(typeRoot.child("daily").child(dayKey), durationSec, distanceMeters)
        updateOutdoorAggBucket(typeRoot.child("weekly").child(weekKey), durationSec, distanceMeters)
        updateOutdoorAggBucket(typeRoot.child("monthly").child(monthKey), durationSec, distanceMeters)
        updateOutdoorBests(typeRoot.child("bests"), avgPaceSecPerKm, distanceMeters, durationSec)

        // ---- ALL rollup ----
        val allRoot = statsRoot.child("ALL")

        updateOutdoorAggBucket(allRoot.child("daily").child(dayKey), durationSec, distanceMeters)
        updateOutdoorAggBucket(allRoot.child("weekly").child(weekKey), durationSec, distanceMeters)
        updateOutdoorAggBucket(allRoot.child("monthly").child(monthKey), durationSec, distanceMeters)

    }
    fun saveOutdoorSession(
        userKey: String,
        activity: ACTIVITY,
        startTime: Long,
        endTime: Long,
        distanceMeters: Int,
        avgHeartRate: Int,
        maxHeartRate: Int,
        tracePointsCount: Int = 0
    ): String? {
        if (!activity.isOutdoor()) {
            Log.e("Outdoor", "saveOutdoorSession called with non-outdoor activity=$activity")
            return null
        }

        val outdoorRootRef = profileRef.child(userKey).child("Activities").child("OutdoorActivity")
        val activityId = outdoorRootRef.push().key ?: return null
        val activityRef = outdoorRootRef.child(activityId)

        val durationSec = ((endTime - startTime) / 1000).toInt().coerceAtLeast(0)

        val dKey = dayKey(startTime)
        val wKey = weekKey(startTime)
        val mKey = monthKey(startTime)

        val dist = distanceMeters.coerceAtLeast(0)

        val avgPaceSecPerKm =
            if (dist > 0 && durationSec > 0) (durationSec * 1000) / dist else 0

        val summary = OutdoorActivitySummary(
            distanceMeters = distanceMeters.coerceAtLeast(0),
            durationSec = durationSec,
            avgHeartRate = avgHeartRate.coerceAtLeast(0),
            maxHeartRate = maxHeartRate.coerceAtLeast(0),
            avgPaceSecPerKm = avgPaceSecPerKm
        )

        val envelope = mapOf(
            "type" to activity.name,
            "startTime" to startTime,
            "endTime" to endTime,
            "durationSec" to durationSec,
            "dayKey" to dKey,
            "weekKey" to wKey,
            "monthKey" to mKey
        )

        val updates = HashMap<String, Any>()
        envelope.forEach { (k, v) -> updates[k] = v as Any }
        updates["distanceMeters"] = summary.distanceMeters
        updates["durationSec"] = summary.durationSec
        updates["avgHeartRate"] = summary.avgHeartRate
        updates["maxHeartRate"] = summary.maxHeartRate
        updates["avgPaceSecPerKm"] = summary.avgPaceSecPerKm
        updates["traceRef"] = activityId
        updates["tracePoints"] = tracePointsCount


        activityRef.updateChildren(updates)
            .addOnSuccessListener {
                updateOutdoorStats(
                    userKey = userKey,
                    activity = activity,
                    dayKey = dKey,
                    weekKey = wKey,
                    monthKey = mKey,
                    durationSec = durationSec,
                    distanceMeters = summary.distanceMeters,
                    avgPaceSecPerKm = summary.avgPaceSecPerKm
                )
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Failed to save outdoor activity", e)
            }
        return activityId
    }

    @SuppressLint("MissingPermission")
    fun startOutdoorSession(context: Context) {
        if (_sessionState.value == OutdoorSessionState.RUNNING) return

        // Reset
        startTimeMs = System.currentTimeMillis()
        accumulatedPauseMs = 0L
        pauseStartedMs = null
        lastLocation = null
        _elapsedSec.postValue(0)
        _distanceMetersLive.postValue(0)
        _speedMpsLive.postValue(0f)

        // Reset trace
        tracePoints.clear()
        lastStoredTraceLocation = null
        lastStoredTraceTimeMs = null
        _traceLatLngLive.postValue(emptyList())


        _sessionState.value = OutdoorSessionState.RUNNING

        // Start timer + GPS
        startTimer()
        startLocationUpdates(context)

        viewModelScope.launch {
            sendOutdoorMetricsToWear(
                _elapsedSec.value ?: 0,
                _distanceMetersLive.value ?: 0,
                mpsToKph(_speedMpsLive.value ?: 0f),
                _sessionState.value
            )
        }
        startOutdoorMetricsStreaming()
    }

    fun pauseOutdoorSession() {
        if (_sessionState.value != OutdoorSessionState.RUNNING) return
        _sessionState.postValue(OutdoorSessionState.PAUSED)
        pauseStartedMs = System.currentTimeMillis()
        stopTimer()
        stopLocationUpdates()
        viewModelScope.launch {
            sendOutdoorMetricsToWear(
                _elapsedSec.value ?: 0,
                _distanceMetersLive.value ?: 0,
                mpsToKph(_speedMpsLive.value ?: 0f),
                OutdoorSessionState.PAUSED
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun resumeOutdoorSession(context: Context) {
        if (_sessionState.value != OutdoorSessionState.PAUSED) return
        val pausedAt = pauseStartedMs
        if (pausedAt != null) accumulatedPauseMs += (System.currentTimeMillis() - pausedAt)
        pauseStartedMs = null

        _sessionState.value = OutdoorSessionState.RUNNING
        startTimer()
        startLocationUpdates(context)
        viewModelScope.launch {
            sendOutdoorMetricsToWear(
                _elapsedSec.value ?: 0,
                _distanceMetersLive.value ?: 0,
                mpsToKph(_speedMpsLive.value ?: 0f),
                OutdoorSessionState.RUNNING
            )
        }
    }

    fun finishOutdoorSession(): Triple<Long, Long, Int> {
        // Stop updates first
        stopTimer()
        stopLocationUpdates()

        val start = startTimeMs ?: System.currentTimeMillis()
        val end = System.currentTimeMillis()
        val distanceMeters = _distanceMetersLive.value ?: 0

        _sessionState.postValue(OutdoorSessionState.IDLE)

        // Keep last values visible or reset — your choice.
        // Here: keep displayed values; UI may navigate away.
        viewModelScope.launch {
            sendOutdoorMetricsToWear(
                _elapsedSec.value ?: 0,
                _distanceMetersLive.value ?: 0,
                mpsToKph(_speedMpsLive.value ?: 0f),
                OutdoorSessionState.IDLE
            )
        }
        return Triple(start, end, distanceMeters)
    }
    private fun startTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            while (_sessionState.value == OutdoorSessionState.RUNNING) {
                val start = startTimeMs ?: System.currentTimeMillis()
                val now = System.currentTimeMillis()
                val elapsedMs = (now - start - accumulatedPauseMs).coerceAtLeast(0L)
                _elapsedSec.postValue((elapsedMs / 1000L).toInt())
                delay(1_000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun maybeAppendTracePoint(loc: android.location.Location) {
        // Filter poor accuracy points
        if (loc.accuracy > TRACE_MAX_ACC_METERS) return

        val nowMs = loc.time
        val lastLoc = lastStoredTraceLocation
        val lastT = lastStoredTraceTimeMs

        val shouldStore = when {
            lastLoc == null || lastT == null -> true
            (nowMs - lastT) >= TRACE_MAX_INTERVAL_MS -> true
            lastLoc.distanceTo(loc) >= TRACE_MIN_DISTANCE_METERS -> true
            else -> false
        }

        if (!shouldStore) return

        tracePoints.add(
            TracePoint(
                lat = loc.latitude,
                lon = loc.longitude,
                t = nowMs,
                acc = loc.accuracy
            )
        )

        // Update UI polyline list (avoid rebuilding from scratch with a map() each time)
        val current = _traceLatLngLive.value.orEmpty()
        val updated = ArrayList<LatLng>(current.size + 1).apply {
            addAll(current)
            add(LatLng(loc.latitude, loc.longitude))
        }
        _traceLatLngLive.postValue(updated)

        lastStoredTraceLocation = android.location.Location(loc)
        lastStoredTraceTimeMs = nowMs
    }

    fun peekTracePoints(): List<TracePoint> = tracePoints.toList()

    fun consumeTracePoints(): List<TracePoint> {
        val out = tracePoints.toList()
        tracePoints.clear()
        lastStoredTraceLocation = null
        lastStoredTraceTimeMs = null
        _traceLatLngLive.postValue(emptyList())
        return out
    }

    fun saveOutdoorTrace(
        userKey: String,
        activityId: String,
        points: List<TracePoint>
    ) {
        if (points.isEmpty()) return

        val traceRoot = profileRef
            .child(userKey)
            .child("Traces")
            .child("Outdoor")
            .child(activityId)
            .child("points")

        // Chunk to keep updateChildren payload reasonable
        val chunkSize = 250
        var idx = 0

        fun writeNextChunk() {
            if (idx >= points.size) {
                Log.w("Firebase", "Trace saved: ${points.size} points for activityId=$activityId")
                return
            }

            val end = minOf(idx + chunkSize, points.size)
            val updates = HashMap<String, Any>(end - idx)

            for (i in idx until end) {
                val key = traceRoot.push().key ?: continue
                updates[key] = points[i]
            }

            idx = end

            traceRoot.updateChildren(updates)
                .addOnSuccessListener { writeNextChunk() }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to save trace chunk for activityId=$activityId", e)
                }
        }

        writeNextChunk()
    }




    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        startUpdatesSeq += 1
        Log.w(
            GPS_TAG,
            "startLocationUpdates(seq=$startUpdatesSeq) from ${caller()} | state=${sessionState.value} | " +
                    "callbackNull=${locationCallback == null} clientNull=${locationClient == null}"
        )

        if (locationClient == null) {
            locationClient = LocationServices.getFusedLocationProviderClient(context)
        }
        val client = locationClient ?: run {
            Log.e(GPS_TAG, "startLocationUpdates(): locationClient still null after init")
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        Log.w(
            GPS_TAG,
            "startLocationUpdates(): priority=HIGH_ACCURACY interval=1000ms " +
                    "minInterval=${request.minUpdateIntervalMillis} minDistance=${request.minUpdateDistanceMeters}"
        )

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return

                    val prevNanos = lastFixElapsedNanos
                    lastFixElapsedNanos = loc.elapsedRealtimeNanos
                    if (prevNanos != null) {
                        val dtSec = (loc.elapsedRealtimeNanos - prevNanos) / 1_000_000_000.0
                        Log.w(GPS_TAG, "fix Δt=${"%.2f".format(dtSec)}s")
                    }

                    Log.w(
                        GPS_TAG,
                        "fix lat=${loc.latitude}, lon=${loc.longitude}, acc=${loc.accuracy}m, " +
                                "speed=${if (loc.hasSpeed()) loc.speed else -1f}m/s, provider=${loc.provider}, time=${loc.time}"
                    )

                    _locationData.postValue(
                        LocationData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            description = ""
                        )
                    )

                    maybeAppendTracePoint(loc)

                    val prev = lastLocation
                    if (prev != null) {
                        val delta = prev.distanceTo(loc)

                        when {
                            delta < 1f -> {
                                Log.w(GPS_TAG, "distance REJECTED: jitter delta=${"%.2f".format(delta)}m")
                            }
                            delta >= 50f -> {
                                Log.w(GPS_TAG, "distance REJECTED: jump/batch delta=${"%.2f".format(delta)}m")
                            }
                            else -> {
                                val newDist = (_distanceMetersLive.value ?: 0) + delta.roundToInt()
                                _distanceMetersLive.postValue(newDist)
                                Log.w(GPS_TAG, "distance ACCEPTED: +${"%.2f".format(delta)}m total=${newDist}m")
                            }
                        }
                    }
                    lastLocation = loc

                    val speed = if (loc.hasSpeed()) loc.speed else 0f
                    _speedMpsLive.postValue(speed)
                    Log.w(GPS_TAG, "speed published: ${"%.2f".format(speed)} m/s")
                }
            }
        }

        val cb = locationCallback ?: run {
            Log.e(GPS_TAG, "startLocationUpdates(): locationCallback still null after init")
            return
        }

        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
            Log.w(GPS_TAG, "startLocationUpdates(): requestLocationUpdates() registered")
        } catch (se: SecurityException) {
            Log.e(GPS_TAG, "startLocationUpdates(): missing location permission at runtime", se)
        } catch (t: Throwable) {
            Log.e(GPS_TAG, "startLocationUpdates(): failed to register location updates", t)
        }
    }


    private fun stopLocationUpdates() {
        val cb = locationCallback ?: return
        locationClient?.removeLocationUpdates(cb)
        Log.w(GPS_TAG, "stopLocationUpdates(): removing location callback")

    }

    fun computeHrSummary(): Pair<Int, Int> {
        val list = _heartRateList
        if (list.isEmpty()) return 0 to 0
        val values = list.map { it.y.toInt() }
        val avg = values.average().roundToInt()
        val max = values.maxOrNull() ?: 0
        return avg to max
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendOutdoorMetricsToWear(
        elapsedSec: Int,
        distanceMeters: Int,
        speedKph: Float,
        state: OutdoorSessionState?
    ) {
        scope.launch {
            try {
                val started = state != OutdoorSessionState.IDLE
                val paused = state == OutdoorSessionState.PAUSED

                val req = PutDataMapRequest.create(WearPaths.OUTDOOR_METRICS).run {
                    dataMap.putInt(OutdoorState.TIME_SEC, elapsedSec)
                    dataMap.putInt(OutdoorState.DIST_M, distanceMeters)
                    dataMap.putFloat(OutdoorState.SPEED_KPH, speedKph)
                    dataMap.putBoolean(OutdoorState.STARTED, started)
                    dataMap.putBoolean(OutdoorState.PAUSED, paused)
                    dataMap.putLong(OutdoorState.NONCE, System.currentTimeMillis()) // or OutdoorState.TS
                    asPutDataRequest().setUrgent()
                }

                Wearable.getDataClient(getApplication())
                    .putDataItem(req)
                    .await()

                Log.d("WearComms", "sent outdoor_metrics t=$elapsedSec d=$distanceMeters v=${speedKph}kph paused=$paused")
            } catch (e: Exception) {
                Log.e("WearComms", "sendOutdoorMetrics failed", e)
            }
        }
    }
    private fun mpsToKph(mps: Float): Float = mps * 3.6f

    private fun startOutdoorMetricsStreaming() {
        metricsTxJob?.cancel()
        metricsTxJob = viewModelScope.launch {
            while (true) {
                val state = _sessionState.value
                if (state == OutdoorSessionState.IDLE) {
                    stopOutdoorMetricsStreaming()
                    return@launch
                }

                val elapsed = _elapsedSec.value ?: 0
                val dist = _distanceMetersLive.value ?: 0
                val speedKph = mpsToKph(_speedMpsLive.value ?: 0f)

                val fingerprint = "$state|$elapsed|$dist|$speedKph"
                if (fingerprint != lastSent) {
                    lastSent = fingerprint
                    sendOutdoorMetricsToWear(elapsed, dist, speedKph, state)
                }

                delay(500)
            }
        }
    }

    private fun stopOutdoorMetricsStreaming() {
        metricsTxJob?.cancel()
        metricsTxJob = null
        lastSent = null
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
