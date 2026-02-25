@file:OptIn(ExperimentalMaterial3Api::class)

package com.epfl.esl.workoutapp.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.epfl.esl.workoutapp.R


@Composable
fun ProfileScreen(
    userKey: String,
    onNavigateToEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("All", "Running", "Cycling", "Swimming", "Workout")

    val vm: ProfileViewModel = viewModel(
        key = userKey,
        factory = ProfileVmFactory(userKey))

    LaunchedEffect(Unit) {
        vm.start()
    }

    val selectedTabKey by vm.selectedTab.collectAsState()
    val overview by vm.overview.collectAsState()
    val highlights by vm.highlights.collectAsState()
    val recent by vm.recent.collectAsState()
    val trend by vm.trend.collectAsState()


    // UI-only: replace with real user/profile data later
    val userName by vm.username.collectAsState()
    val profileImageUrl by vm.profileImageUrl.collectAsState()

    val selectedTabIndex = remember(selectedTabKey) {
        tabs.indexOf(selectedTabKey).coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                ProfileHeader(
                    userName = userName,
                    handle = userKey,
                    profileImageUrl = profileImageUrl,
                    onEditProfile = onNavigateToEdit
                )
            }

            item {
                SectionTitle(
                    title = "Overview",
                    subtitle = "Quick snapshot"
                )
                OverviewRow(cards = overview)
            }

            item {
                Spacer(Modifier.height(12.dp))
                ActivityTabs(
                    tabs = tabs,
                    selectedIndex = selectedTabIndex,
                    onSelect = { idx -> vm.setTab(tabs[idx]) }
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionTitle(title = "Highlights", subtitle = "Personal bests and key stats")
                HighlightsList(items = highlights)
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionTitle(title = "Trend", subtitle = "Last 8 weeks")
                TrendCard(
                    tabKey = selectedTabKey,
                    points = trend,
                    labelForWeek = vm::weekKeyToApproxDateLabel
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionTitle(title = "Recent", subtitle = "Latest sessions")
            }

            items(recent) { entry ->
                RecentEntryRow(entry = entry)
            }
        }
    }
}

class ProfileVmFactory(private val userKey: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ProfileViewModel(userKey) as T
    }
}

@Composable
private fun ProfileHeader(
    userName: String,
    handle: String,
    profileImageUrl: String?,
    onEditProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profileImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = userName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(userName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedButton(onClick = onEditProfile) { Text("Edit") }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OverviewRow(cards: List<StatCardModel>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cards) { card ->
            ElevatedCard(
                modifier = Modifier.width(160.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(card.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(card.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(card.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActivityTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun HighlightsList(items: List<HighlightModel>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        items.forEach { h ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(h.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(h.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrendCard(
    tabKey: String,
    points: List<TrendPoint>,
    labelForWeek: (String) -> String
) {
    val maxValue = remember(points) { points.maxOfOrNull { it.value } ?: 0 }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = when (tabKey) {
                    "Workout" -> "Reps per week"
                    "Running", "Cycling", "Swimming" -> "Distance per week"
                    else -> "Sessions per week"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            if (points.isEmpty() || maxValue <= 0) {
                Text(
                    text = "No data yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEach { p ->
                        val raw = if (maxValue > 0) (p.value.toFloat() / maxValue.toFloat()) else 0f
                        val frac = raw.coerceIn(0f, 1f)

                        val minFrac = 0.10f
                        val displayFrac = if (p.value == 0) minFrac else frac


                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((56.dp * displayFrac))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            )
                            Spacer(Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = labelForWeek(p.weekKey),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentEntryRow(entry: RecentEntryModel) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(entry.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(entry.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
