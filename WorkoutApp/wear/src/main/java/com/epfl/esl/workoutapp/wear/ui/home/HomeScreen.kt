package com.epfl.esl.workoutapp.wear.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.epfl.esl.workoutapp.wear.ui.common.components.DebugImuCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.epfl.esl.workoutapp.wear.domain.ActivityType
import com.epfl.esl.workoutapp.wear.navigation.Routes
import com.epfl.esl.workoutapp.wear.navigation.SessionStore
import com.epfl.esl.workoutapp.wear.ui.common.DefaultWearSummary
import com.epfl.esl.workoutapp.wear.ui.common.WearSummaryScreen

@Composable
fun HomeScreen(
    onGoIndoor: () -> Unit,
    onGoOutdoor: () -> Unit,
) {
    val ctx = LocalContext.current.applicationContext
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(ctx))
    val ui = vm.ui.collectAsState().value

    val sessionType = SessionStore.type.collectAsState().value
    val summary = SessionStore.summary.collectAsState().value ?: DefaultWearSummary
    // auto navigation if mobile command sets session
    LaunchedEffect(sessionType) {
        when (sessionType) {
            is ActivityType.Indoor -> onGoIndoor()
            is ActivityType.Outdoor -> onGoOutdoor()
            null -> Unit
        }
    }

    // Wear-friendly: content should stay away from edges on round screens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProfileHeader(
            username = ui.username,
            photo = ui.photo?.asImageBitmap(),
            heartRate = ui.heartRate
        )
        WearSummaryScreen(summary = summary)
//        if (ui.showImuDebug) {
//            DebugImuCard(imu = ui.imu)
//        }
    }
}

/* ----------------- small reusable UI pieces ----------------- */

@Composable
fun ProfileHeader(
    username: String,
    photo: ImageBitmap?,
    heartRate: Int?,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = {},
        enabled = false,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface,
            endBackgroundColor = MaterialTheme.colors.surface
        ),
        contentColor = MaterialTheme.colors.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    androidx.compose.foundation.Image(
                        bitmap = photo,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = username.take(1).uppercase().ifBlank { "?" },
                        style = MaterialTheme.typography.title3
                    )
                }
            }

            Spacer(Modifier.width(5.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = username.ifBlank { "—" },
                    style = MaterialTheme.typography.title3,
                    maxLines = 1
                )
//                Text(
//                    text = "HR $heartRate",
//                    style = MaterialTheme.typography.caption2,
//                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
//                )
            }

            // A small “status dot” (always green when app is running; you can later bind it)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00C853))
            )
        }
    }
}

@Composable
fun RoundActionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.25f),
            endBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.25f)
        ),
        contentColor = MaterialTheme.colors.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.title3,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}
