package com.epfl.esl.workoutapp.mobile.ui.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NewActivityScreen(
    userKey: String,
    onOutdoorSelected: (ACTIVITY) -> Unit,
    onIndoorSelected: (WORKOUT) -> Unit,
    modifier: Modifier = Modifier,
    newActivityViewModel: NewActivityViewModel = viewModel()
) {
    val category by newActivityViewModel.category.observeAsState(initial = null)
    val navEvent by newActivityViewModel.navEvent.observeAsState(initial = null)

    // One-time navigation handling
    navEvent?.let { event ->
        when (event) {
            is NewActivityNavEvent.GoOutdoor -> onOutdoorSelected(event.activity)
            is NewActivityNavEvent.GoIndoor -> onIndoorSelected(event.workout)
        }
        newActivityViewModel.consumeNavEvent()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            AnimatedContent(
                targetState = category,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn())
                        .togetherWith(slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "new_activity_step"
            ) { state ->
                when (state) {
                    null -> CategorySelectionStep(
                        onIndoor = { newActivityViewModel.selectCategory(ActivityCategory.INDOOR) },
                        onOutdoor = { newActivityViewModel.selectCategory(ActivityCategory.OUTDOOR) }
                    )

                    ActivityCategory.INDOOR -> ActivityPickerStep(
                        headerTitle = "INDOOR",
                        headerSubtitle = "Choose a workout",
                        options = listOf(
                            BigOption(
                                title = "Bench press",
                                icon = Icons.Filled.FitnessCenter,
                                onClick = { newActivityViewModel.pickIndoor(WORKOUT.BENCH) }
                            ),
                            BigOption(
                                title = "Squat",
                                icon = Icons.Filled.AccessibilityNew,
                                onClick = { newActivityViewModel.pickIndoor(WORKOUT.SQUAT) }
                            ),
                            BigOption(
                                title = "Deadlift",
                                icon = Icons.Filled.Whatshot,
                                onClick = { newActivityViewModel.pickIndoor(WORKOUT.DEADLIFT) }
                            )
                        ),
                        onChangeCategory = { newActivityViewModel.resetCategory() }
                    )

                    ActivityCategory.OUTDOOR -> ActivityPickerStep(
                        headerTitle = "OUTDOOR",
                        headerSubtitle = "Choose an activity",
                        options = listOf(
                            BigOption(
                                title = "Running",
                                icon = Icons.Filled.DirectionsRun,
                                onClick = { newActivityViewModel.pickOutdoor(ACTIVITY.RUNNING) }
                            ),
                            BigOption(
                                title = "Cycling",
                                icon = Icons.Filled.DirectionsBike,
                                onClick = { newActivityViewModel.pickOutdoor(ACTIVITY.CYCLING) }
                            ),
                            BigOption(
                                title = "Swimming",
                                icon = Icons.Filled.Pool,
                                onClick = { newActivityViewModel.pickOutdoor(ACTIVITY.SWIMMING) }
                            )
                        ),
                        onChangeCategory = { newActivityViewModel.resetCategory() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelectionStep(
    onIndoor: () -> Unit,
    onOutdoor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        Text(
            text = "New activity",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose a category",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )

        Spacer(Modifier.height(8.dp))

        // Two large options that more-or-less fill the page
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BigCategoryCard(
                title = "Indoor",
                subtitle = "Strength training",
                icon = Icons.Filled.Home,
                gradient = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                        MaterialTheme.colorScheme.primaryContainer
                    )
                ),
                onClick = onIndoor,
                modifier = Modifier.weight(1f)
            )

            BigCategoryCard(
                title = "Outdoor",
                subtitle = "Cardio / endurance",
                icon = Icons.Filled.Landscape,
                gradient = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.90f),
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                ),
                onClick = onOutdoor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActivityPickerStep(
    headerTitle: String,
    headerSubtitle: String,
    options: List<BigOption>,
    onChangeCategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Chosen option “at the top of the page in bold”
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = headerSubtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }

        // Three large option cards that take up most of the page
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.take(3).forEach { opt ->
                BigActionCard(
                    title = opt.title,
                    icon = opt.icon,
                    onClick = opt.onClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        OutlinedButton(
            onClick = onChangeCategory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Change category")
        }
    }
}

@Composable
private fun BigCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(gradient)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(34.dp),
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BigActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class BigOption(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)
