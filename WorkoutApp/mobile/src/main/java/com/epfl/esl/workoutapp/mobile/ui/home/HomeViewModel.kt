package com.epfl.esl.workoutapp.mobile.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

class HomeViewModel : ViewModel() {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val profileRef: DatabaseReference = database.getReference("Profiles")

    private val _selectedTab = MutableStateFlow(HomeTab.ME)
    val selectedTab: StateFlow<HomeTab> = _selectedTab

    private val _myFeed = MutableStateFlow<List<FeedItem>>(emptyList())
    val myFeed: StateFlow<List<FeedItem>> = _myFeed

    private val _friendsFeed = MutableStateFlow<List<FeedItem>>(emptyList())
    val friendsFeed: StateFlow<List<FeedItem>> = _friendsFeed

    private val _friends = MutableStateFlow<List<String>>(emptyList())
    val friends: StateFlow<List<String>> = _friends

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _friendNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val friendNames: StateFlow<Map<String, String>> = _friendNames

    data class FriendEntry(val key: String, val username: String)

    private val _friendsList = MutableStateFlow<List<FriendEntry>>(emptyList())
    val friendsList: StateFlow<List<FriendEntry>> = _friendsList

    private val _routePreviews = MutableStateFlow<Map<String, List<RoutePoint>>>(emptyMap())
    val routePreviews: StateFlow<Map<String, List<RoutePoint>>> = _routePreviews

    private val _fullRoutes = MutableStateFlow<Map<String, List<RoutePoint>>>(emptyMap())
    val fullRoutes: StateFlow<Map<String, List<RoutePoint>>> = _fullRoutes

    private val inFlightFullRouteKeys = HashSet<String>()


    private val inFlightRouteKeys = HashSet<String>()

    data class FriendRequestEntry(
        val fromKey: String,
        val fromUsername: String,
        val ts: Long
    )

    private val _incomingRequests = MutableStateFlow<List<FriendRequestEntry>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequestEntry>> = _incomingRequests

    data class OutgoingRequestEntry(
        val toKey: String,
        val ts: Long
    )


    private val _outgoingRequests = MutableStateFlow<List<OutgoingRequestEntry>>(emptyList())
    val outgoingRequests: StateFlow<List<OutgoingRequestEntry>> = _outgoingRequests

    fun loadOutgoingRequests(myKey: String) {
        profileRef.child(myKey).child("FriendRequests").child("outgoing")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { child ->
                        val toKey = child.key ?: return@mapNotNull null
                        val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                        OutgoingRequestEntry(toKey = toKey, ts = ts)
                    }.sortedByDescending { it.ts }
                    _outgoingRequests.value = list
                }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                }
            })
    }

    fun cancelFriendRequest(myKey: String, toKey: String, onResult: (Boolean, String?) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "Profiles/$myKey/FriendRequests/outgoing/$toKey" to null,
            "Profiles/$toKey/FriendRequests/incoming/$myKey" to null
        )
        database.reference.updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }



    fun setTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun loadMyFeed(myKey: String, perKindLimit: Int = 5) {
        _loading.value = true
        _error.value = null

        loadUserRecentActivities(
            ownerKey = myKey,
            ownerLabel = "You",
            perKindLimit = perKindLimit,
            onComplete = { items, err ->
                if (err != null) _error.value = err
                _myFeed.value = items.sortedByDescending { it.startTime }
                _loading.value = false
            }
        )
    }

    fun loadFriendsFeed(myKey: String, perKindLimit: Int = 5, mergedLimit: Int = 30) {
        _loading.value = true
        _error.value = null

        // 1) Read friend keys
        profileRef.child(myKey).child("Friends")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val friendKeys = snapshot.children.mapNotNull { it.key }.distinct()
                    _friends.value = friendKeys

                    val people = friendKeys.distinct()
                    if (people.isEmpty()) {
                        _friendsFeed.value = emptyList()
                        _loading.value = false
                        return
                    }
                    loadFriendNames(myKey) {
                        val allItems = mutableListOf<FeedItem>()
                        val pending = AtomicInteger(people.size)

                        people.forEach { personKey ->
                            val nameMap = _friendNames.value
                            val label = nameMap[personKey] ?: shortKey(personKey)

                            loadUserRecentActivities(
                                ownerKey = personKey,
                                ownerLabel = label,
                                perKindLimit = perKindLimit,
                                onComplete = { items, err ->
                                    if (err != null && _error.value == null) _error.value = err
                                    allItems.addAll(items)

                                    if (pending.decrementAndGet() == 0) {
                                        _friendsFeed.value = allItems
                                            .sortedByDescending { it.startTime }
                                            .take(mergedLimit)
                                        _loading.value = false
                                    }
                                }
                            )
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                    _loading.value = false
                }
            })
    }

    private fun loadUserRecentActivities(
        ownerKey: String,
        ownerLabel: String,
        perKindLimit: Int,
        onComplete: (List<FeedItem>, String?) -> Unit
    ) {
        val out = mutableListOf<FeedItem>()
        val pending = AtomicInteger(2)
        var firstErr: String? = null

        fun doneOne() {
            if (pending.decrementAndGet() == 0) onComplete(out, firstErr)
        }

        // Outdoor
        profileRef.child(ownerKey).child("Activities").child("OutdoorActivity")
            .orderByChild("startTime")
            .limitToLast(perKindLimit)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val startTime = child.child("startTime").getValue(Long::class.java) ?: 0L
                        val durationSec = child.child("durationSec").getValue(Int::class.java) ?: 0
                        val type = child.child("type").getValue(String::class.java) ?: "OUTDOOR"
                        val workoutType = child.child("workoutType").getValue(String::class.java)
                        val dist = child.child("distanceMeters").getValue(Int::class.java)
                        val pace = child.child("avgPaceSecPerKm").getValue(Int::class.java)
                        val traceRef = child.child("traceRef").getValue(String::class.java)
                        val tracePoints = child.child("tracePoints").getValue(Int::class.java)

                        out.add(
                            FeedItem(
                                ownerKey = ownerKey,
                                ownerLabel = ownerLabel,
                                kind = FeedKind.OUTDOOR,
                                type = type,
                                workoutType = workoutType,
                                startTime = startTime,
                                durationSec = durationSec,
                                distanceMeters = dist,
                                avgPaceSecPerKm = pace,
                                traceRef = traceRef,
                                tracePoints = tracePoints
                            )
                        )
                    }
                    doneOne()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (firstErr == null) firstErr = error.message
                    doneOne()
                }
            })

        // Indoor
        profileRef.child(ownerKey).child("Activities").child("IndoorActivity")
            .orderByChild("startTime")
            .limitToLast(perKindLimit)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val startTime = child.child("startTime").getValue(Long::class.java) ?: 0L
                        val durationSec = child.child("durationSec").getValue(Int::class.java) ?: 0
                        val type = child.child("type").getValue(String::class.java) ?: "INDOOR"
                        val workoutType = child.child("workoutType").getValue(String::class.java)
                        val totalSets = child.child("totalSets").getValue(Int::class.java)
                        val totalVolume = child.child("totalVolume").getValue(Int::class.java)
                        val bestSetReps = child.child("bestSetReps").getValue(Int::class.java)
                        val bestSetWeightKg = child.child("bestSetWeightKg").getValue(Int::class.java)

                        out.add(
                            FeedItem(
                                ownerKey = ownerKey,
                                ownerLabel = ownerLabel,
                                kind = FeedKind.INDOOR,
                                type = type,
                                workoutType = workoutType,
                                startTime = startTime,
                                durationSec = durationSec,
                                totalSets = totalSets,
                                totalVolume = totalVolume,
                                bestSetReps = bestSetReps,
                                bestSetWeightKg = bestSetWeightKg
                            )
                        )
                    }
                    doneOne()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (firstErr == null) firstErr = error.message
                    doneOne()
                }
            })
    }

    private fun shortKey(key: String): String {
        // cosmetic only for now
        return if (key.length <= 8) key else key.take(4) + "…" + key.takeLast(3)
    }

    private data class TP(val lat: Double, val lon: Double, val t: Long)

    private fun tracePointsRef(ownerKey: String, traceRef: String): DatabaseReference =
        profileRef.child(ownerKey)
            .child("Traces")
            .child("Outdoor")
            .child(traceRef)
            .child("points")

    private fun readTP(snap: DataSnapshot): List<TP> {
        val out = ArrayList<TP>(snap.childrenCount.toInt().coerceAtLeast(0))
        snap.children.forEach { p ->
            val lat = p.child("lat").getValue(Double::class.java) ?: return@forEach
            val lon = p.child("lon").getValue(Double::class.java) ?: return@forEach
            val t = p.child("t").getValue(Long::class.java) ?: 0L
            out.add(TP(lat = lat, lon = lon, t = t))
        }
        return out
    }

    fun ensureFullRoute(
        ownerKey: String,
        traceRef: String,
        maxPoints: Int = 2000
    ) {
        val cacheKey = "$ownerKey:$traceRef"
        if (_fullRoutes.value.containsKey(cacheKey)) return
        synchronized(inFlightFullRouteKeys) {
            if (inFlightFullRouteKeys.contains(cacheKey)) return
            inFlightFullRouteKeys.add(cacheKey)
        }

        val ref = tracePointsRef(ownerKey, traceRef)
        ref.orderByChild("t")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    synchronized(inFlightFullRouteKeys) { inFlightFullRouteKeys.remove(cacheKey) }

                    val dedup = readTP(snapshot)
                        .sortedBy { it.t }
                        .distinctBy { it.t }

                    val sampled = uniformSample(dedup, maxPoints)
                    val route = sampled.map { RoutePoint(lat = it.lat, lon = it.lon) }

                    _fullRoutes.value = _fullRoutes.value + (cacheKey to route)
                }

                override fun onCancelled(error: DatabaseError) {
                    synchronized(inFlightFullRouteKeys) { inFlightFullRouteKeys.remove(cacheKey) }
                    if (_error.value == null) _error.value = error.message
                }
            })
    }



    fun ensureRoutePreview(
        ownerKey: String,
        traceRef: String,
        maxPoints: Int = 120
        ) {
        val cacheKey = "$ownerKey:$traceRef"
        if (_routePreviews.value.containsKey(cacheKey)) return
        synchronized(inFlightRouteKeys) {
            if (inFlightRouteKeys.contains(cacheKey)) return
            inFlightRouteKeys.add(cacheKey)
        }

        val half = (maxPoints / 2).coerceAtLeast(20)
        val traceRoot = profileRef
            .child(ownerKey)
            .child("Traces")
            .child("Outdoor")
            .child(traceRef)
            .child("points")

        data class TP(val lat: Double, val lon: Double, val t: Long)
        fun readPoints(snap: DataSnapshot): List<TP> {
            val out = ArrayList<TP>(snap.childrenCount.toInt().coerceAtLeast(0))
            snap.children.forEach { p ->
                val lat = p.child("lat").getValue(Double::class.java) ?: return@forEach
                val lon = p.child("lon").getValue(Double::class.java) ?: return@forEach
                val t = p.child("t").getValue(Long::class.java) ?: 0L
                out.add(TP(lat = lat, lon = lon, t = t))
            }
            return out
        }

        val merged = ArrayList<TP>()
        val pending = AtomicInteger(2)
        var firstErr: String? = null

        fun finishIfDone() {
            if (pending.decrementAndGet() != 0) return

            synchronized(inFlightRouteKeys) { inFlightRouteKeys.remove(cacheKey) }

            if (firstErr != null) {
                // Non-fatal: keep the feed usable even if preview fails.
                if (_error.value == null) _error.value = firstErr
                return
            }
            // Merge + sort + de-dup on timestamp
            val dedup = merged
                .sortedBy { it.t }
                .distinctBy { it.t }

            val sampled = uniformSample(dedup, maxPoints)
            val route = sampled.map { RoutePoint(lat = it.lat, lon = it.lon) }
             _routePreviews.value = _routePreviews.value + (cacheKey to route)
        }

        traceRoot.orderByChild("t").limitToFirst(half)
            .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                merged.addAll(readPoints(snapshot))
                finishIfDone()
            }
            override fun onCancelled(error: DatabaseError) {
                if (firstErr == null) firstErr = error.message
                finishIfDone()
            }
        })

        traceRoot.orderByChild("t").limitToLast(half)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                merged.addAll(readPoints(snapshot))
                finishIfDone()
             }

            override fun onCancelled(error: DatabaseError) {
                   if (firstErr == null) firstErr = error.message
                   finishIfDone()
            }
            })
        }

   private fun <T> uniformSample(points: List<T>, maxOut: Int): List<T> {
       if (maxOut <= 0) return emptyList()
       if (points.size <= maxOut) return points
       if (maxOut == 1) return listOf(points.first())

       val out = ArrayList<T>(maxOut)
       val n = points.size
       for (k in 0 until maxOut) {
           val idx = ((k.toDouble() * (n - 1)) / (maxOut - 1)).toInt()
           out.add(points[idx])
       }
       return out
   }
    private fun loadFriendNames(myKey: String, onDone: () -> Unit) {
        profileRef.child(myKey).child("FriendsMeta")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val map = snapshot.children.mapNotNull { child ->
                        val friendKey = child.key ?: return@mapNotNull null
                        val username = child.child("username").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        friendKey to username
                    }.toMap()
                    _friendNames.value = map
                    onDone()
                }

                override fun onCancelled(error: DatabaseError) {
                    onDone()
                }
            })
    }

    fun loadFriendsList(myKey: String) {
        profileRef.child(myKey).child("Friends")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val keys = snapshot.children.mapNotNull { it.key }.distinct()
                    if (keys.isEmpty()) {
                        _friendsList.value = emptyList()
                        return
                    }

                    // Pull cached names
                    profileRef.child(myKey).child("FriendsMeta")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(metaSnap: DataSnapshot) {
                                val nameMap = metaSnap.children.mapNotNull { c ->
                                    val k = c.key ?: return@mapNotNull null
                                    val u = c.child("username").getValue(String::class.java)
                                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                    k to u
                                }.toMap()

                                _friendsList.value = keys.map { k ->
                                    FriendEntry(k, nameMap[k] ?: shortKey(k))
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                _friendsList.value = keys.map { k -> FriendEntry(k, shortKey(k)) }
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    _friendsList.value = emptyList()
                    _error.value = error.message
                }
            })
    }
    fun loadIncomingRequests(myKey: String) {
        profileRef.child(myKey).child("FriendRequests").child("incoming")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { child ->
                        val fromKey = child.key ?: return@mapNotNull null
                        val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                        val fromUsername = child.child("fromUsername").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: shortKey(fromKey)

                        FriendRequestEntry(
                            fromKey = fromKey,
                            fromUsername = fromUsername,
                            ts = ts
                        )
                    }.sortedByDescending { it.ts }

                    _incomingRequests.value = list
                }

                override fun onCancelled(error: DatabaseError) {
                    _error.value = error.message
                }
            })
    }

    fun sendFriendRequest(myKey: String, otherKey: String, onResult: (Boolean, String?) -> Unit) {
        val toKey = otherKey.trim()
        if (toKey.isEmpty()) return onResult(false, "Friend code is empty.")
        if (toKey == myKey) return onResult(false, "You cannot add yourself.")

        // First: validate target exists
        profileRef.child(toKey).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(targetSnap: DataSnapshot) {
                if (!targetSnap.exists()) return onResult(false, "No user found with that code.")

                // Second: read my friendship/request state in one shot
                profileRef.child(myKey).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(meSnap: DataSnapshot) {

                        // Already friends?
                        if (meSnap.child("Friends").hasChild(toKey)) {
                            return onResult(false, "You are already friends.")
                        }

                        // Outgoing already exists?
                        if (meSnap.child("FriendRequests").child("outgoing").hasChild(toKey)) {
                            return onResult(false, "Friend request already sent.")
                        }

                        // Crossed request? (They already requested you)
                        if (meSnap.child("FriendRequests").child("incoming").hasChild(toKey)) {
                            // Auto-accept is usually the least confusing UX
                            return acceptFriendRequest(myKey, toKey, onResult)
                        }

                        val myUsername = meSnap.child("username").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: shortKey(myKey)

                        val now = System.currentTimeMillis()
                        val updates = hashMapOf<String, Any>(
                            "Profiles/$toKey/FriendRequests/incoming/$myKey/ts" to now,
                            "Profiles/$toKey/FriendRequests/incoming/$myKey/fromUsername" to myUsername,
                            "Profiles/$myKey/FriendRequests/outgoing/$toKey/ts" to now
                        )

                        database.reference.updateChildren(updates)
                            .addOnSuccessListener { onResult(true, null) }
                            .addOnFailureListener { e -> onResult(false, e.message) }
                    }

                    override fun onCancelled(error: DatabaseError) = onResult(false, error.message)
                })
            }

            override fun onCancelled(error: DatabaseError) = onResult(false, error.message)
        })
    }



    fun acceptFriendRequest(myKey: String, fromKey: String, onResult: (Boolean, String?) -> Unit) {
        val incomingRef = profileRef.child(myKey).child("FriendRequests").child("incoming").child(fromKey)

        incomingRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(reqSnap: DataSnapshot) {
                if (!reqSnap.exists()) {
                    return onResult(false, "Friend request no longer exists.")
                }

                val fromUsername = reqSnap.child("fromUsername").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: shortKey(fromKey)

                profileRef.child(myKey).child("username")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(meSnap: DataSnapshot) {
                            val myUsername = meSnap.getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() } ?: shortKey(myKey)

                            val now = System.currentTimeMillis()
                            val updates = hashMapOf<String, Any?>(
                                // mutual friendship edges
                                "Profiles/$myKey/Friends/$fromKey/sinceTs" to now,
                                "Profiles/$fromKey/Friends/$myKey/sinceTs" to now,

                                // mutual cached usernames for UI
                                "Profiles/$myKey/FriendsMeta/$fromKey/username" to fromUsername,
                                "Profiles/$fromKey/FriendsMeta/$myKey/username" to myUsername,

                                // cleanup requests
                                "Profiles/$myKey/FriendRequests/incoming/$fromKey" to null,
                                "Profiles/$fromKey/FriendRequests/outgoing/$myKey" to null
                            )

                            database.reference.updateChildren(updates)
                                .addOnSuccessListener { onResult(true, null) }
                                .addOnFailureListener { e -> onResult(false, e.message) }
                        }

                        override fun onCancelled(error: DatabaseError) = onResult(false, error.message)
                    })
            }

            override fun onCancelled(error: DatabaseError) = onResult(false, error.message)
        })
    }

    fun declineFriendRequest(myKey: String, fromKey: String, onResult: (Boolean, String?) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "Profiles/$myKey/FriendRequests/incoming/$fromKey" to null,
            "Profiles/$fromKey/FriendRequests/outgoing/$myKey" to null
        )
        database.reference.updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }
    fun removeFriendMutual(myKey: String, friendKey: String, onResult: (Boolean, String?) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "Profiles/$myKey/Friends/$friendKey" to null,
            "Profiles/$friendKey/Friends/$myKey" to null,

            // optional: also remove cached meta
            "Profiles/$myKey/FriendsMeta/$friendKey" to null,
            "Profiles/$friendKey/FriendsMeta/$myKey" to null
        )

        database.reference.updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

}



enum class HomeTab { ME, FRIENDS }
