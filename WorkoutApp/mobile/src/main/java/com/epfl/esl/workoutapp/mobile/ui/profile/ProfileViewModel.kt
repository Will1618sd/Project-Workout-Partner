package com.epfl.esl.workoutapp.mobile.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil.imageLoader
import coil.request.ImageRequest
import com.epfl.esl.workoutapp.R
import com.epfl.esl.workoutapp.mobile.service.WearPaths
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


// UI models (shared with ProfileScreen)
data class StatCardModel(val title: String, val value: String, val caption: String)
data class HighlightModel(val label: String, val value: String, val note: String)
data class RecentEntryModel(val type: String, val title: String, val subtitle: String, val dateLabel: String)
data class TrendPoint(val weekKey: String, val value: Int)

class ProfileViewModel(
    private val userKey: String
) : ViewModel() {

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val profileRef: DatabaseReference = db.getReference("Profiles").child(userKey)
    var storageRef = FirebaseStorage.getInstance().getReference()

    private val _username = MutableStateFlow("")
    private val _profileImageUrl = MutableStateFlow<String?>(null)
    private val _profileImageUri = MutableStateFlow<Uri?>(null)

    private val _weight = MutableStateFlow(0)
    private val _height = MutableStateFlow(0)
    val username: StateFlow<String> = _username
    val profileImageUrl: StateFlow<String?> = _profileImageUrl
    val profileImageUri: StateFlow<Uri?> = _profileImageUri
    val weight: StateFlow<Int> = _weight
    val height: StateFlow<Int> = _height



    private val _selectedTab = MutableStateFlow("All")
    val selectedTab: StateFlow<String> = _selectedTab

    private val _overview = MutableStateFlow<List<StatCardModel>>(emptyList())
    val overview: StateFlow<List<StatCardModel>> = _overview

    private val _highlights = MutableStateFlow<List<HighlightModel>>(emptyList())
    val highlights: StateFlow<List<HighlightModel>> = _highlights

    private val _trend = MutableStateFlow<List<TrendPoint>>(emptyList())
    val trend: StateFlow<List<TrendPoint>> = _trend

    private val _allRecent = MutableStateFlow<List<RecentEntryModel>>(emptyList())

    // Internal buffers for 2-branch merge
    private var indoorRecent: List<Pair<Long, RecentEntryModel>> = emptyList()
    private var outdoorRecent: List<Pair<Long, RecentEntryModel>> = emptyList()

    private var indoorRecentListener: ValueEventListener? = null
    private var outdoorRecentListener: ValueEventListener? = null

    // Filtered "Recent" based on selected tab
    val recent: StateFlow<List<RecentEntryModel>> =
        combine(_selectedTab, _allRecent) { tab, list ->
            when (tab) {
                "All" -> list
                "Workout" -> list.filter { it.type == "W" }
                "Running" -> list.filter { it.type == "R" }
                "Cycling" -> list.filter { it.type == "C" }
                "Swimming" -> list.filter { it.type == "S" }
                else -> list
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun start() {
        loadRecent()
        loadOverviewAndHighlightsForTab(_selectedTab.value)
        loadTrendForTab(_selectedTab.value)
        loadProfileHeader()
        loadPhysicalStats()
    }

    private fun loadProfileHeader() {
        // Username
        profileRef.child("username").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java)
                _username.value = name ?: "Unknown User"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileVM", "Failed to load username", error.toException())
            }
        })

        // Profile image URL (stored in Realtime DB)
        profileRef.child("photo URL").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val url = snapshot.getValue(String::class.java)
                _profileImageUrl.value = url
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileVM", "Failed to load photo URL", error.toException())
            }
        })
    }

    private fun loadPhysicalStats() {
        // Load Weight
        profileRef.child("weight").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _weight.value = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Load Height
        profileRef.child("height").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _height.value = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun updateProfileImage(uri: Uri, context: Context, dataClient: DataClient) {
        val auth = FirebaseAuth.getInstance()
        val email = auth.currentUser?.email

        if (email != null) {
            // Strict requirement: Store as <email>.jpg
            val storageRef = FirebaseStorage.getInstance().getReference("ProfileImages/$email.jpg")

            storageRef.putFile(uri)
                .addOnSuccessListener {
                    // Upload successful.
                    // We fetch the NEW download URL to refresh the UI immediately.
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val newUrlString = downloadUri.toString()
                        _profileImageUri.value = downloadUri
                        _profileImageUrl.value = newUrlString
                        profileRef.child("photo URL").setValue(newUrlString)
//                        sendImageToWear(uri, context, dataClient)
                    }
                }
                .addOnFailureListener {
                    Log.e("ProfileVM", "Image upload failed", it)
                }
        }
    }

    fun updateProfile(newUsername: String, newWeight: Int?, newHeight: Int?, context: Context?, dataClient: DataClient) {
        val updates = mutableMapOf<String, Any>()

        updates["username"] = newUsername

        if (newWeight != null) updates["weight"] = newWeight
        if (newHeight != null) updates["height"] = newHeight

        profileRef.updateChildren(updates)
            .addOnSuccessListener { Log.d("ProfileVM", "Profile updated") }
            .addOnFailureListener { Log.e("ProfileVM", "Update failed") }

        sendDataToWear(context, dataClient)
    }

    fun setTab(tab: String) {
        _selectedTab.value = tab
        loadOverviewAndHighlightsForTab(tab)
        loadTrendForTab(tab)
    }

    // -----------------------------
    // Firebase Reads
    // -----------------------------

    private fun loadRecent() {
        // NEW SCHEMA:
        // Profiles/{userKey}/Activities/IndoorActivity/{id}
        // Profiles/{userKey}/Activities/OutdoorActivity/{id}

        val activitiesRoot = profileRef.child("Activities")
        val indoorRef = activitiesRoot.child("IndoorActivity")
        val outdoorRef = activitiesRoot.child("OutdoorActivity")

        // Take more than 20 from each branch to allow meaningful merge.
        val n = 40

        // Clean up previous listeners if start() is called again
        indoorRecentListener?.let { indoorRef.removeEventListener(it) }
        outdoorRecentListener?.let { outdoorRef.removeEventListener(it) }

        indoorRecentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                indoorRecent = snapshot.children.mapNotNull { node ->
                    val startTime = node.child("startTime").getValue(Long::class.java) ?: return@mapNotNull null
                    val model = parseSessionNode(node) ?: return@mapNotNull null
                    startTime to model
                }
                publishMergedRecent()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileVM", "Indoor loadRecent cancelled: ${error.message}", error.toException())
            }
        }

        outdoorRecentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                outdoorRecent = snapshot.children.mapNotNull { node ->
                    val startTime = node.child("startTime").getValue(Long::class.java) ?: return@mapNotNull null
                    val model = parseSessionNode(node) ?: return@mapNotNull null
                    startTime to model
                }
                publishMergedRecent()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileVM", "Outdoor loadRecent cancelled: ${error.message}", error.toException())
            }
        }

        indoorRef.orderByChild("startTime")
            .limitToLast(n)
            .addValueEventListener(indoorRecentListener as ValueEventListener)

        outdoorRef.orderByChild("startTime")
            .limitToLast(n)
            .addValueEventListener(outdoorRecentListener as ValueEventListener)
    }

    private fun loadTrendForTab(tab: String) {
        val statsRoot = statsRootForTab(tab)
        val weeks = lastNWeekKeys(8)

        // Fetch enough weeks; we'll map by key and then fill missing ones.
        statsRoot.child("weekly")
            .orderByKey()
            .limitToLast(16) // a bit more than 8 to be safe across gaps
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    // Build a map from weekKey -> DataSnapshot
                    val byKey: Map<String, DataSnapshot> =
                        snapshot.children.associateBy { it.key.orEmpty() }

                    val points = weeks.map { wk ->
                        val wkNode = byKey[wk]

                        val sessions = wkNode?.child("sessions")?.getValue(Int::class.java) ?: 0
                        val distanceMeters = wkNode?.child("distanceMeters")?.getValue(Int::class.java)
                        val totalReps = wkNode?.child("totalReps")?.getValue(Int::class.java)

                        val value = when (tab) {
                            "Workout" -> totalReps ?: sessions
                            "Running", "Cycling", "Swimming" -> distanceMeters ?: sessions
                            else -> sessions
                        }

                        TrendPoint(weekKey = wk, value = value)
                    }

                    _trend.value = points
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileVM", "loadTrendForTab cancelled: ${error.message}", error.toException())
                    // Still publish 8 zeros so the UI shows an empty baseline instead of disappearing
                    _trend.value = lastNWeekKeys(8).map { TrendPoint(it, 0) }
                }
            })
    }



    private fun publishMergedRecent() {
        val merged = (indoorRecent + outdoorRecent)
            .sortedByDescending { it.first }   // startTime descending
            .take(20)
            .map { it.second }

        _allRecent.value = merged
    }

    /**
     * Parse a single session node. In your NEW schema, the fields are stored directly under {id}.
     */
    private fun parseSessionNode(node: DataSnapshot): RecentEntryModel? {
        val typeRaw = node.child("type").getValue(String::class.java) ?: return null
        val startTime = node.child("startTime").getValue(Long::class.java) ?: 0L
        val durationSec = node.child("durationSec").getValue(Int::class.java) ?: 0

        return when (typeRaw) {
            "WORKOUT" -> {
                // Indoor (Workout) saved with fields directly at session root
                val subtype = node.child("workoutType").getValue(String::class.java) ?: "WORKOUT"
                val reps = node.child("totalReps").getValue(Int::class.java) ?: 0
                val volume = node.child("totalVolume").getValue(Int::class.java) ?: 0

                RecentEntryModel(
                    type = "W",
                    title = "Workout",
                    subtitle = "$subtype • $reps reps • $volume volume • ${formatHms(durationSec)}",
                    dateLabel = formatRelativeDate(startTime)
                )
            }

            "RUNNING", "CYCLING", "SWIMMING" -> {
                // Outdoor saved with fields directly at session root
                val distM = node.child("distanceMeters").getValue(Int::class.java) ?: 0
                val pace = node.child("avgPaceSecPerKm").getValue(Int::class.java) ?: 0

                val typeLetter = when (typeRaw) {
                    "RUNNING" -> "R"
                    "CYCLING" -> "C"
                    "SWIMMING" -> "S"
                    else -> typeRaw.take(1)
                }

                val title = typeRaw.lowercase().replaceFirstChar { it.uppercase() }

                RecentEntryModel(
                    type = typeLetter,
                    title = title,
                    subtitle = "${formatKm(distM)} • ${formatHms(durationSec)} • ${formatPace(pace)}",
                    dateLabel = formatRelativeDate(startTime)
                )
            }

            else -> null
        }
    }

    private fun loadOverviewAndHighlightsForTab(tab: String) {
        loadOverview(tab)
        loadHighlights(tab)
    }

    private fun loadOverview(tab: String) {
        val statsRoot = statsRootForTab(tab)
        val wk = currentWeekKey()

        statsRoot.child("weekly").child(wk)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sessions = snapshot.child("sessions").getValue(Int::class.java) ?: 0
                    val activeSec = snapshot.child("activeSec").getValue(Int::class.java) ?: 0

                    // Optional extra cards if present (Outdoor uses distanceMeters; Indoor uses totalReps/totalVolume)
                    val distanceMeters = snapshot.child("distanceMeters").getValue(Int::class.java)
                    val totalReps = snapshot.child("totalReps").getValue(Int::class.java)

                    val cards = mutableListOf(
                        StatCardModel("This week", sessions.toString(), "sessions"),
                        StatCardModel("Active time", formatHms(activeSec), "this week"),
                    )

                    if (distanceMeters != null) {
                        cards.add(StatCardModel("Distance", formatKmValue(distanceMeters), "this week"))
                    }
                    if (totalReps != null) {
                        cards.add(StatCardModel("Reps", totalReps.toString(), "this week"))
                    }

                    _overview.value = cards
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileVM", "loadOverview cancelled: ${error.message}", error.toException())
                }
            })
    }

    private fun loadHighlights(tab: String) {
        val bestsRef = bestsRefForTab(tab)

        bestsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<HighlightModel>()

                if (tab == "Workout") {
                    val bestSetReps = snapshot.child("bestSetReps").getValue(Int::class.java) ?: 0
                    val bestSetWeight = snapshot.child("bestSetWeightKg").getValue(Int::class.java) ?: 0
                    val bestSessionVolume = snapshot.child("bestSessionVolume").getValue(Int::class.java) ?: 0
                    val longestSessionSec = snapshot.child("longestSessionSec").getValue(Int::class.java) ?: 0

                    list.add(HighlightModel("Max reps (set)", bestSetReps.toString(), "Best"))
                    list.add(HighlightModel("Max weight (set)", "${bestSetWeight} kg", "Best"))
                    list.add(HighlightModel("Best session volume", bestSessionVolume.toString(), "Best"))
                    list.add(HighlightModel("Longest session", formatHms(longestSessionSec), "Best"))
                } else {
                    // Outdoor bests fields (RUNNING/CYCLING/SWIMMING). For "All", you may not have bests written.
                    val fastestPace = snapshot.child("fastestAvgPaceSecPerKm").getValue(Int::class.java) ?: 0
                    val longestDist = snapshot.child("longestDistanceMeters").getValue(Int::class.java) ?: 0
                    val longestSessionSec = snapshot.child("longestSessionSec").getValue(Int::class.java) ?: 0
                    val bestCalories = snapshot.child("bestSessionCalories").getValue(Int::class.java) ?: 0

                    if (fastestPace > 0) list.add(HighlightModel("Best pace", formatPace(fastestPace), "Fastest avg"))
                    if (longestDist > 0) list.add(HighlightModel("Longest distance", formatKmValue(longestDist), "Best"))
                    if (longestSessionSec > 0) list.add(HighlightModel("Longest session", formatHms(longestSessionSec), "Best"))
                    if (bestCalories > 0) list.add(HighlightModel("Most calories", bestCalories.toString(), "Best"))
                }

                _highlights.value = list
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileVM", "loadHighlights cancelled: ${error.message}", error.toException())
            }
        })
    }

    fun sendDataToWear(context: Context?, dataClient: DataClient) {
        if (context == null) return

        // Launch in background to prevent UI freeze and allow network operations
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.w("ProfileVM", "In Dispatcher ok")
                // 1. Use Coil to load the image (Handles both Local Content Uri and Remote HTTPS Url)
                val loader = imageLoader(context)
                Log.w("ProfileVM", "Loader ok")
                val request = ImageRequest.Builder(context)
                    .data(_profileImageUri.value ?: R.drawable.user_image) // Fallback to default ID if null
                    .allowHardware(false) // Disable hardware bitmaps so we can read the pixels
                    .build()
                Log.w("ProfileVM", "Request ok")

                val result = loader.execute(request)
                Log.w("ProfileVM", "Result ok")
                val originalBitmap = (result.drawable as? BitmapDrawable)?.bitmap

                Log.w("ProfileVM", "Bitmap loaded")

                if (originalBitmap == null) {
                    Log.e("ProfileVM", "Failed to load bitmap for Wear")
                    return@launch
                }

                // 2. Resize Logic (Preserving your ratio logic)
                val ratio = 13f
                val targetWidth = (originalBitmap.width / ratio).toInt().coerceAtLeast(1)
                val targetHeight = (originalBitmap.height / ratio).toInt().coerceAtLeast(1)

                val scaledBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    targetWidth,
                    targetHeight,
                    false
                )

                Log.w("ProfileVM", "Bitmap scaled")

                // 3. Compress to PNG
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val imageByteArray = stream.toByteArray()

                Log.w("ProfileVM", "Stream ok")

                // 4. Send Data to Wear
                val requestWear = PutDataMapRequest.create(WearPaths.USER_INFO).run {
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    dataMap.putByteArray("profileImage", imageByteArray)
                    dataMap.putString("username", _username.value ?: "")
                    asPutDataRequest()
                }

                Log.w("ProfileVM", "Request ok")

                requestWear.setUrgent()
                dataClient.putDataItem(requestWear)
                    .addOnSuccessListener { Log.d("ProfileVM", "Data sent to Wear successfully") }
                    .addOnFailureListener { Log.e("ProfileVM", "Failed to send data to Wear", it) }

            } catch (e: Exception) {
                Log.e("ProfileVM", "Crash during sendDataToWear", e)
            }
        }
    }

    fun sendDataToFireBase(context: Context) {
        // 1. Update Text Data (Username, Weight, Height)
        // Note: profileRef already points to "Profiles/{userKey}", so we just child the fields.
        Log.w("ProfileVM", "In sendDataToFireBase")
        val updates = mapOf(
            "username" to _username.value,
            "weight" to _weight.value,
            "height" to _height.value
        )


        profileRef.updateChildren(updates)
            .addOnSuccessListener { Log.d("ProfileVM", "Text data updated successfully") }
            .addOnFailureListener { Log.e("ProfileVM", "Failed to update text data", it) }

        // 2. Update Image (Only if it's a new local URI)
        val currentUri = _profileImageUri.value ?: return
        Log.w("ProfileVM", "Current URI ok")


        // Check if the URI is local (content:// or file://).
        // If it starts with 'http', it's already on the server, so we skip upload.
//        val isLocalUri = currentUri.scheme?.let { it.startsWith("content") || it.startsWith("file") } ?: false

//        if (isLocalUri) {
        Log.w("ProfileVM", "Local URI detected")
        val auth = FirebaseAuth.getInstance()
        val email = auth.currentUser?.email

        if (email != null) {
            Log.w("ProfileVM", "Email detected")
            val storageRef = FirebaseStorage.getInstance().getReference("ProfileImages/$email.jpg")

            // Upload the file directly (putFile is efficient and handles background uploads)
            storageRef.putFile(currentUri)
                .addOnSuccessListener {
                    Log.w("ProfileVM", "In Listener success")
                    // 3. Get the new Download URL and save it to Database
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val newUrlString = downloadUri.toString()

                        // Update local state to the new remote URL
                        _profileImageUrl.value = newUrlString

                        // Update Database
                        profileRef.child("photo URL").setValue(newUrlString)
                            .addOnSuccessListener { Log.d("ProfileVM", "Photo URL updated") }
                    }
                }
                .addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(@NonNull exception: java.lang.Exception) {
                        val errorCode = (exception as StorageException).getErrorCode()
                        val errorMessage = exception.message
                        // Log these to find the real culprit (e.g., Code 403 = Permission Denied)
                        Log.e("ProfileVM", "Upload failed: " + errorCode + " - " + errorMessage)
                    }
                })
        }
//        }
    }

    // -----------------------------
    // Firebase path helpers
    // -----------------------------

    private fun statsRootForTab(tab: String): DatabaseReference {
        return when (tab) {
            // Workout stats are stored under Stats/WORKOUT/overall/...
            "Workout" -> profileRef.child("Stats").child("INDOOR").child("overall")

            // Outdoor stats are stored under Stats/RUNNING, Stats/CYCLING, Stats/SWIMMING
            "Running" -> profileRef.child("Stats").child("RUNNING")
            "Cycling" -> profileRef.child("Stats").child("CYCLING")
            "Swimming" -> profileRef.child("Stats").child("SWIMMING")

            // ALL rollup exists for aggregates; may not include bests depending on your writer
            "All" -> profileRef.child("Stats").child("ALL")

            else -> profileRef.child("Stats").child("ALL")
        }
    }

    private fun bestsRefForTab(tab: String): DatabaseReference =
        statsRootForTab(tab).child("bests")

    // -----------------------------
    // Formatting helpers
    // -----------------------------

    private fun currentWeekKey(): String {
        val cal = Calendar.getInstance()
        return "%04d-W%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.WEEK_OF_YEAR)
        )
    }

    private fun lastNWeekKeys(n: Int): List<String> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4

        val keys = ArrayList<String>(n)
        repeat(n) {
            val year = cal.get(Calendar.YEAR)
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            keys.add("%04d-W%02d".format(year, week))
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        }
        keys.reverse() // oldest -> newest
        return keys
    }

    fun weekKeyToApproxDateLabel(weekKey: String): String {
        // Label as "MMM d" for the Monday of that ISO week, e.g. "Jan 5"
        // Falls back to the original weekKey if parsing fails.
        return try {
            val parts = weekKey.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()

            val cal = Calendar.getInstance()
            cal.clear()
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.minimalDaysInFirstWeek = 4
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.WEEK_OF_YEAR, week)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

            SimpleDateFormat("MMM d", Locale.US).format(cal.time)
        } catch (_: Exception) {
            weekKey
        }
    }



    private fun formatHms(totalSec: Int): String {
        val sec = totalSec.coerceAtLeast(0)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun formatPace(secPerKm: Int): String {
        if (secPerKm <= 0) return "—"
        val m = secPerKm / 60
        val s = secPerKm % 60
        return "%d:%02d /km".format(m, s)
    }

    private fun formatKm(distanceMeters: Int): String =
        "${formatKmValue(distanceMeters)} km"

    private fun formatKmValue(distanceMeters: Int): String {
        val km = distanceMeters.coerceAtLeast(0) / 1000.0
        return String.format(Locale.US, "%.1f", km)
    }

    private fun formatRelativeDate(startTimeMs: Long): String {
        if (startTimeMs <= 0L) return "—"

        val now = System.currentTimeMillis()
        val diffMs = now - startTimeMs
        val dayMs = 24L * 60L * 60L * 1000L

        return when {
            diffMs < dayMs -> "Today"
            diffMs < 2 * dayMs -> "Yesterday"
            diffMs < 7 * dayMs -> "${diffMs / dayMs} days ago"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(startTimeMs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        val activitiesRoot = profileRef.child("Activities")
        val indoorRef = activitiesRoot.child("IndoorActivity")
        val outdoorRef = activitiesRoot.child("OutdoorActivity")

        indoorRecentListener?.let { indoorRef.removeEventListener(it) }
        outdoorRecentListener?.let { outdoorRef.removeEventListener(it) }

        indoorRecentListener = null
        outdoorRecentListener = null
    }
}
