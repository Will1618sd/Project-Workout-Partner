package com.epfl.esl.workoutapp.wear.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme


@Composable
fun ActivityHeader(
    title: String,
    icon: ImageVector,
    subtitle: String?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colors.primary
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, style = MaterialTheme.typography.title3)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.caption2)
            }
        }
    }
}