package com.epfl.esl.workoutapp.mobile.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.android.gms.maps.model.LatLng
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState


@Composable
fun HomeScreen(
    userKey: String,
    onOpenFriendsManage: () -> Unit,
    onOpenOutdoorMap: (ownerKey: String, traceRef: String, item: FeedItem) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    var showAddFriend by remember { mutableStateOf(false) }
    var addFriendError by remember { mutableStateOf<String?>(null) }

    val tab by vm.selectedTab.collectAsState()
    val myFeed by vm.myFeed.collectAsState()
    val friendsFeed by vm.friendsFeed.collectAsState()
    val friends by vm.friends.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(userKey) {
        if (userKey.isNotBlank()) {
            vm.loadMyFeed(userKey)
            vm.loadFriendsFeed(userKey)
        }
    }


    val stats = remember(myFeed) { computeHomeStats(myFeed) }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = if (tab == HomeTab.ME) 0 else 1) {
                Tab(
                    selected = tab == HomeTab.ME,
                    onClick = { vm.setTab(HomeTab.ME) },
                    text = { Text("Me") }
                )
                Tab(
                    selected = tab == HomeTab.FRIENDS,
                    onClick = { vm.setTab(HomeTab.FRIENDS) },
                    text = { Text("Friends") }
                )
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
        ) {
            if (tab == HomeTab.ME) {
                item {
                    SummaryHeaderVibrant(stats)
                }
                item {
                    ChallengesCardVibrant(stats)
                }
            }

            // Feedback
            if (loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (error != null) {
                item {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Feed area
            when (tab) {
                HomeTab.ME -> {
                    if (myFeed.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No activities yet",
                                body = "Start an activity to see it here."
                            )
                        }
                    } else {
                        feedSection(myFeed, vm, onOpenOutdoorMap)
                    }
                }
                HomeTab.FRIENDS -> {
                    if (friends.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onOpenFriendsManage) { Text("Manage") }
                            }
                        }
                        item {
                            EmptyState(
                                title = "No friends yet",
                                body = "Add a friend by their code to see their activity.",
                                actionText = "Add friend",
                                onAction = { showAddFriend = true  }
                            )
                        }
                    } else if (friendsFeed.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onOpenFriendsManage) { Text("Manage") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { showAddFriend = true }) { Text("Add friend") }
                            }
                        }
                        item {
                            EmptyState(
                                title = "No recent activity",
                                body = "Your friends have no recent activities to display."
                            )
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onOpenFriendsManage) { Text("Manage") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { showAddFriend = true }) { Text("Add friend") }
                            }
                        }
                        feedSection(friendsFeed, vm, onOpenOutdoorMap)
                    }
                }
            }
        }
    }
    if (showAddFriend) {
        AddFriendDialog(
            onDismiss = { showAddFriend = false; addFriendError = null },
            onAdd = { friendKey ->
                vm.sendFriendRequest(userKey, friendKey) { ok, msg ->
                    if (ok) {
                        showAddFriend = false
                        addFriendError = null
                    } else {
                        addFriendError = msg ?: "Failed to add friend."
                    }
                }
            },
            errorText = addFriendError
        )
    }

}

private fun LazyListScope.feedSection(
    items: List<FeedItem>,
    vm: HomeViewModel,
    onOpenOutdoorMap: (String, String, FeedItem) -> Unit
) {
    val grouped = groupByDayBucket(items)
    grouped.forEach { (header, bucketItems) ->
        item(key = "hdr_$header") { SectionHeader(header) }
        items(
            items = bucketItems,
            key = { it.ownerKey + "_" + it.kind.name + "_" + it.startTime }
        ) { item ->
            FeedCardPretty(item, vm, onOpenOutdoorMap = onOpenOutdoorMap)
        }
    }
}



@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}


private fun prettyType(item: FeedItem): String =
    item.type.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }

private fun primarySummary(item: FeedItem): String {
    val duration = formatDuration(item.durationSec)
    return when (item.kind) {
        FeedKind.OUTDOOR -> {
            val km = item.distanceMeters?.let { it / 1000.0 }
            val kmStr = if (km != null) "${(km * 10).roundToInt() / 10.0} km" else "—"
            "$duration · $kmStr"
        }
        FeedKind.INDOOR -> {
            val sets = item.totalSets?.toString() ?: "—"
            val vol = item.totalVolume?.toString() ?: "—"
            "$duration · $sets sets · $vol volume"
        }
    }
}

private fun secondarySummary(item: FeedItem): String? {
    return when (item.kind) {
        FeedKind.OUTDOOR -> {
            val pace = item.avgPaceSecPerKm?.takeIf { it > 0 }?.let { formatPace(it) }
            listOfNotNull(pace).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
        FeedKind.INDOOR -> {
            val reps = item.bestSetReps?.takeIf { it > 0 }?.let { "Best reps: $it" }
            val w = item.bestSetWeightKg?.takeIf { it > 0 }?.let { "Best weight: ${it}kg" }
            listOfNotNull(reps, w).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }
}

private fun formatDuration(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}

private fun formatPace(secPerKm: Int): String {
    val s = secPerKm.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "%d:%02d /km".format(m, r)
}

private fun formatTime(ts: Long): String {
    if (ts <= 0L) return ""
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return fmt.format(Date(ts))
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    errorText: String?
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add friend") },
        text = {
            Column {
                Text("Enter your friend’s code (their userKey).")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    label = { Text("Friend code") }
                )
                if (errorText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(code) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun groupByDayBucket(items: List<FeedItem>): List<Pair<String, List<FeedItem>>> {
    val now = System.currentTimeMillis()
    fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val today0 = startOfDay(now)
    val yesterday0 = today0 - 24L * 60 * 60 * 1000

    val (today, rest1) = items.partition { it.startTime >= today0 }
    val (yesterday, earlier) = rest1.partition { it.startTime in yesterday0 until today0 }

    val out = mutableListOf<Pair<String, List<FeedItem>>>()
    if (today.isNotEmpty()) out += "Today" to today
    if (yesterday.isNotEmpty()) out += "Yesterday" to yesterday
    if (earlier.isNotEmpty()) out += "Earlier" to earlier
    return out
}

@Composable
private fun FeedCardPretty(
    item: FeedItem,
    vm: HomeViewModel,
    onOpenOutdoorMap: (String, String, FeedItem) -> Unit,
    modifier: Modifier = Modifier) {

    val canOpenMap = item.kind == FeedKind.OUTDOOR && !item.traceRef.isNullOrBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canOpenMap) Modifier.clickable {
                    onOpenOutdoorMap(item.ownerKey, item.traceRef!!, item)
                } else Modifier
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            LeadingIcon(item)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.ownerLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = formatDateShort(item.startTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                Spacer(Modifier.height(6.dp))

                // Main label
                Text(
                    text = prettyTypeLabel(item),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(10.dp))

                // Metrics row
                MetricsRow(item)

                // Secondary line (optional)
                val secondary = secondarySummaryPretty(item)
                if (secondary != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }
}

@Composable
private fun RouteThumbnail(points: List<RoutePoint>, modifier: Modifier = Modifier) {
        // Simple, lightweight polyline thumbnail (no GoogleMap in LazyColumn).
    val outline = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas

            // Compute bounds
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY

            for (p in points) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }

            val w = size.width
            val h = size.height
            val pad = 6f

            val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1e-9
            val lonSpan = (maxLon - minLon).takeIf { it > 0 } ?: 1e-9

            // Preserve aspect ratio by scaling to fit both spans.
            val sx = (w - 2 * pad) / lonSpan.toFloat()
            val sy = (h - 2 * pad) / latSpan.toFloat()
            val s = minOf(sx, sy)
            val x0 = pad + (w - 2 * pad - (lonSpan.toFloat() * s)) / 2f
            val y0 = pad + (h - 2 * pad - (latSpan.toFloat() * s)) / 2f

            fun toOffset(p: RoutePoint): Offset {
                val x = x0 + ((p.lon - minLon).toFloat() * s)
                // Invert Y so north is up
                val y = y0 + ((maxLat - p.lat).toFloat() * s)
                return Offset(x, y)
            }

            // Border
            drawRect(
                color = outline,
                size = size,
                style = Stroke(width = 1f)
            )

            val path = Path()
            val first = toOffset(points.first())
            path.moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val o = toOffset(points[i])
                path.lineTo(o.x, o.y)
            }

            drawPath(
                path = path,
                color = line,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun LeadingIcon(item: FeedItem) {
    val icon = iconFor(item)
    val (bg, fg) = leadingColors(item)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg
        )
    }
}

@Composable
private fun leadingColors(item: FeedItem): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (item.kind) {
        FeedKind.OUTDOOR -> cs.tertiaryContainer to cs.onTertiaryContainer
        FeedKind.INDOOR -> cs.primaryContainer to cs.onPrimaryContainer
    }
}

@Composable
private fun AssistChipSmall(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, maxLines = 1) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun MetricsRow(item: FeedItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetricPill(Icons.Default.Timer, formatDuration(item.durationSec))

        when (item.kind) {
            FeedKind.OUTDOOR -> {
                MetricPill(Icons.Default.Straighten, formatDistance(item.distanceMeters))
                val pace = item.avgPaceSecPerKm?.takeIf { it > 0 }?.let { formatPace(it) } ?: "—"
                MetricPill(Icons.Default.DirectionsRun, pace)
            }
            FeedKind.INDOOR -> {
                val sets = item.totalSets?.toString() ?: "—"
                MetricPill(Icons.Default.FitnessCenter, "$sets sets")
                MetricPill(Icons.Default.LocalFireDepartment, formatVolume(item.totalVolume))
            }
        }
    }
}

@Composable
private fun MetricPill(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, overflow = TextOverflow.Ellipsis)
    }
}

private fun prettyTypeLabel(item: FeedItem): String {
    val rawType = when (item.kind) {
        FeedKind.OUTDOOR -> item.type
        FeedKind.INDOOR -> item.workoutType ?: item.type
    }

    val t = rawType.uppercase(Locale.getDefault())

    return when (t) {
        "BENCH" -> "Bench Press"
        "SQUAT" -> "Squat"
        "DEADLIFT" -> "Deadlift"
        "RUNNING" -> "Running"
        "CYCLING" -> "Cycling"
        "SWIMMING" -> "Swimming"
        else -> t.replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.titlecase() }
    }
}


private fun iconFor(item: FeedItem): ImageVector {
    val t = item.type.uppercase(Locale.getDefault())
    return when (t) {
        "RUNNING" -> Icons.Default.DirectionsRun
        "CYCLING" -> Icons.Default.DirectionsBike
        "SWIMMING" -> Icons.Default.Pool
        "BENCH", "SQUAT", "DEADLIFT" -> Icons.Default.FitnessCenter
        else -> if (item.kind == FeedKind.OUTDOOR) Icons.Default.DirectionsRun else Icons.Default.FitnessCenter
    }
}

private fun formatDistance(meters: Int?): String {
    val m = meters ?: return "—"
    if (m <= 0) return "—"
    val km = m / 1000.0
    val rounded = kotlin.math.round(km * 10) / 10.0
    return "${rounded} km"
}

private fun formatVolume(volume: Int?): String {
    val v = volume ?: return "—"
    if (v <= 0) return "—"
    val nf = java.text.NumberFormat.getIntegerInstance()
    return "${nf.format(v)} kg"
}

private fun formatDateShort(ts: Long): String {
    if (ts <= 0L) return ""
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault()) // Jan 19
    return fmt.format(Date(ts))
}


private fun secondarySummaryPretty(item: FeedItem): String? {
    return when (item.kind) {
        FeedKind.OUTDOOR -> null
        FeedKind.INDOOR -> {
            val reps = item.bestSetReps?.takeIf { it > 0 }?.let { "Best set: $it reps" }
            val w = item.bestSetWeightKg?.takeIf { it > 0 }?.let { "${it} kg" }
            if (reps != null && w != null) "$reps · $w" else reps ?: w
        }
    }
}

data class HomeStats(
    val weekSessions: Int,
    val weekActiveMin: Int,
    val streakDays: Int,
    val challengeSessionsProgress: Pair<Int, Int>, // (done, goal)
    val challengeDistanceProgressKm: Pair<Double, Double> // (doneKm, goalKm)
)

private fun computeHomeStats(myFeed: List<FeedItem>): HomeStats {
    val now = System.currentTimeMillis()

    fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun startOfWeek(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        // Monday start (typical for Europe); change if you prefer Sunday.
        cal.firstDayOfWeek = java.util.Calendar.MONDAY
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun dayKey(ms: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(ms))
    }

    val weekStart = startOfWeek(now)
    val weekItems = myFeed.filter { it.startTime >= weekStart }

    val weekSessions = weekItems.size
    val weekActiveMin = (weekItems.sumOf { it.durationSec.coerceAtLeast(0) } / 60)

    // Streak: count consecutive days ending today with >=1 activity
    val daysWithActivity = myFeed
        .filter { it.startTime > 0L }
        .map { dayKey(it.startTime) }
        .toSet()

    var streak = 0
    var cursor = startOfDay(now)
    while (daysWithActivity.contains(dayKey(cursor))) {
        streak += 1
        cursor -= 24L * 60 * 60 * 1000
    }

    // Challenge 1: 3 activities this week
    val sessionsGoal = 3
    val sessionsDone = weekSessions.coerceAtMost(sessionsGoal)

    // Challenge 2: 10 km outdoor distance this week (all outdoor types)
    val distanceGoalKm = 10.0
    val distanceDoneKm = weekItems
        .filter { it.kind == FeedKind.OUTDOOR }
        .sumOf { (it.distanceMeters ?: 0).coerceAtLeast(0) } / 1000.0

    return HomeStats(
        weekSessions = weekSessions,
        weekActiveMin = weekActiveMin,
        streakDays = streak,
        challengeSessionsProgress = sessionsDone to sessionsGoal,
        challengeDistanceProgressKm = distanceDoneKm to distanceGoalKm
    )
}

@Composable
private fun SummaryHeader(stats: HomeStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            title = "This week",
            primary = "${stats.weekSessions} sessions",
            secondary = "${stats.weekActiveMin} min active",
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Streak",
            primary = "${stats.streakDays} days",
            secondary = if (stats.streakDays == 0) "Start today" else "Keep it going",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(primary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChallengesCard(stats: HomeStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Challenges", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            // Challenge 1
            val (sDone, sGoal) = stats.challengeSessionsProgress
            ChallengeRow(
                title = "Complete $sGoal activities this week",
                progressText = "$sDone / $sGoal",
                progress = (sDone.toFloat() / sGoal.toFloat()).coerceIn(0f, 1f)
            )

            Spacer(Modifier.height(12.dp))

            // Challenge 2
            val (kmDone, kmGoal) = stats.challengeDistanceProgressKm
            ChallengeRow(
                title = "Reach ${formatKm(kmGoal)} km outdoors this week",
                progressText = "${formatKm(kmDone)} / ${formatKm(kmGoal)} km",
                progress = (kmDone / kmGoal).toFloat().coerceIn(0f, 1f)
            )
        }
    }
}

@Composable
private fun ChallengeRow(
    title: String,
    progressText: String,
    progress: Float
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(progressText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatKm(km: Double): String {
    val rounded = kotlin.math.round(km * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
private fun SummaryHeaderVibrant(stats: HomeStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientSummaryCard(
            title = "This week",
            icon = Icons.Default.Bolt,
            primary = "${stats.weekSessions} sessions",
            secondary = "${stats.weekActiveMin} min active",
            modifier = Modifier.weight(1f),
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer
                )
            )
        )

        GradientSummaryCard(
            title = "Streak",
            icon = Icons.Default.LocalFireDepartment, // flame
            primary = "${stats.streakDays} days",
            secondary = if (stats.streakDays == 0) "Light it up today" else "Keep the fire going",
            modifier = Modifier.weight(1f),
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.primaryContainer
                )
            )
        )
    }
}

@Composable
private fun GradientSummaryCard(
    title: String,
    icon: ImageVector,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    brush: Brush
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        // Important: don’t fight the gradient; keep container neutral
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()              // ensures gradient spans full card width
                .background(brush)           // draw gradient behind everything
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(primary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(secondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
private fun ChallengesCardVibrant(stats: HomeStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Challenges", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))

                val (sDone, sGoal) = stats.challengeSessionsProgress
                ChallengeRow(
                    title = "Complete $sGoal activities this week",
                    progressText = "$sDone / $sGoal",
                    progress = (sDone.toFloat() / sGoal.toFloat()).coerceIn(0f, 1f)
                )

                Spacer(Modifier.height(12.dp))

                val (kmDone, kmGoal) = stats.challengeDistanceProgressKm
                ChallengeRow(
                    title = "Reach ${formatKm(kmGoal)} km outdoors this week",
                    progressText = "${formatKm(kmDone)} / ${formatKm(kmGoal)} km",
                    progress = (kmDone / kmGoal).toFloat().coerceIn(0f, 1f)
                )
            }
        }
    }
}

@Composable
fun OutdoorMapDetailScreen(
    ownerKey: String,
    traceRef: String,
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val fullRoutes by vm.fullRoutes.collectAsState()

    val cacheKey = "$ownerKey:$traceRef"
    val routePoints = fullRoutes[cacheKey].orEmpty()
    val latLng = routePoints.map { LatLng(it.lat, it.lon) }

    LaunchedEffect(cacheKey) {
        vm.ensureFullRoute(ownerKey, traceRef, maxPoints = 2000)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Trace of Activity",
            style = MaterialTheme.typography.titleLarge
        )

        RouteMapCard(traceLatLng = latLng)

    }
}

@Composable
fun RouteMapCard(
    traceLatLng: List<LatLng>,
    modifier: Modifier = Modifier,
    zoom: Float = 16f
) {
    if (traceLatLng.isEmpty()) return

    val center = traceLatLng.last()
    val camState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, zoom)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .size(3000.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camState,
            properties = MapProperties(mapType = MapType.NORMAL)
        ) {
            if (traceLatLng.size >= 2) {
                Polyline(
                    points = traceLatLng,
                    color = Color(0xFFFF4B33),
                    width = 6f
                )
            }
        }
    }
}
