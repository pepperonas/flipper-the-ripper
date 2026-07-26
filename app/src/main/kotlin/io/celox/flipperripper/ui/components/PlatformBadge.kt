package io.celox.flipperripper.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.celox.flipperripper.domain.model.Platform

@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(platform.displayName) },
        colors = AssistChipDefaults.assistChipColors(),
        modifier = modifier.padding(vertical = 0.dp),
    )
}
