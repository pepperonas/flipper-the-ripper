package io.celox.flipperripper.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.isActive
import io.celox.flipperripper.ui.components.MorphingMotif
import io.celox.flipperripper.ui.motion.springEntrance
import io.celox.flipperripper.util.MediaIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val records by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAll) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.history_clear_all),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(records, key = { _, r -> r.id }) { index, record ->
                    DownloadCard(
                        record = record,
                        modifier = Modifier.springEntrance(index),
                        onCancel = { viewModel.cancel(record.id) },
                        onRetry = { viewModel.retry(record.id) },
                        onDelete = { viewModel.delete(record.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingMotif(modifier = Modifier.size(72.dp), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadCard(
    record: DownloadRecord,
    modifier: Modifier,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(record)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        record.fileName ?: record.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${record.platform.displayName} · ${statusLabel(record)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(record.status),
                    )
                }
                if (record.status == DownloadStatus.RUNNING) {
                    ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
                }
            }

            if (record.status.isActive) {
                Spacer(Modifier.height(14.dp))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            record.errorMessage?.takeIf { record.status == DownloadStatus.FAILED }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (record.status) {
                    DownloadStatus.COMPLETED ->
                        TextButton(
                            onClick = {
                                MediaIntents.viewIntent(record.mediaUri, record.mode)
                                    ?.let { context.startActivity(it) }
                            },
                            enabled = record.mediaUri != null,
                        ) { Text(stringResource(R.string.history_open)) }
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.history_cancel)) }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED ->
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.history_delete)) }
            }
        }
    }
}

@Composable
private fun Thumbnail(record: DownloadRecord) {
    val shape = RoundedCornerShape(16.dp)
    if (record.thumbnailUrl != null) {
        AsyncImage(
            model = record.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(width = 96.dp, height = 56.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(width = 96.dp, height = 56.dp).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            MorphingMotif(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun statusLabel(record: DownloadRecord): String =
    when (record.status) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.RUNNING -> "Downloading…"
        DownloadStatus.COMPLETED ->
            "Saved" + (record.sizeBytes?.let { " · ${formatSize(it)}" } ?: "") +
                if (record.mode == DownloadMode.AUDIO) " · audio" else ""
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }

@Composable
private fun statusColor(status: DownloadStatus) =
    when (status) {
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    return when {
        bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(java.util.Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
