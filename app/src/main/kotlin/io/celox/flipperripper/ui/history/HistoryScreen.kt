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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.celox.flipperripper.R
import io.celox.flipperripper.data.engine.DownloadNaming
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.isActive
import io.celox.flipperripper.ui.components.EmptyDownloadsMark
import io.celox.flipperripper.ui.components.VideoPlaceholder
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing
import io.celox.flipperripper.util.MediaIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val records by viewModel.history.collectAsStateWithLifecycle()
    // Clearing the history cannot be undone, so the button only *asks*; nothing is removed until the
    // dialog is confirmed. Survives rotation, so a config change can never silently drop the question.
    var askClearAll by rememberSaveable { mutableStateOf(false) }

    if (askClearAll) {
        ClearHistoryDialog(
            entryCount = records.size,
            onConfirm = {
                askClearAll = false
                viewModel.clearAll()
            },
            onDismiss = { askClearAll = false },
        )
    }

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
                        IconButton(onClick = { askClearAll = true }) {
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
            val listState = rememberLazyListState()
            // A new download is prepended (history is newest-first). Jump back to the top whenever the
            // leading entry changes, so the download that was just started is always the one on screen —
            // otherwise pasting a link while scrolled down appeared to do nothing.
            val newestId = records.first().id
            LaunchedEffect(newestId) { listState.animateScrollToItem(0) }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                itemsIndexed(records, key = { _, r -> r.id }) { _, record ->
                    DownloadCard(
                        record = record,
                        // No entrance animation: in a lazy list it re-fires every time a card scrolls
                        // back into view, which reads as flicker. Motion is reserved for the download.
                        modifier = Modifier,
                        onCancel = { viewModel.cancel(record.id) },
                        onRetry = { viewModel.retry(record.id) },
                        onDelete = { viewModel.delete(record.id) },
                    )
                }
            }
        }
    }
}

/**
 * Asks before the whole history goes.
 *
 * Deliberately names what is *not* lost: clearing only drops the list entries — the downloaded files
 * stay in Movies/FlipperTheRipper. Without that sentence the safe choice looks like the risky one.
 */
@Composable
fun ClearHistoryDialog(entryCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = { Text(pluralStringResource(R.plurals.history_clear_body, entryCount, entryCount)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.history_clear_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyDownloadsMark(modifier = Modifier.size(Sizes.emptyMotif))
        Spacer(Modifier.height(Spacing.xl))
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(Modifier.height(Spacing.sm))
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
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(record)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        DownloadNaming.displayTitle(record.fileName, record.title),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
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
                Spacer(Modifier.height(Spacing.md))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            record.errorMessage?.takeIf { record.status == DownloadStatus.FAILED }?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when (record.status) {
                    DownloadStatus.COMPLETED -> {
                        TextButton(
                            onClick = {
                                MediaIntents.viewIntent(record.mediaUri, record.mode)
                                    ?.let { context.startActivity(it) }
                            },
                            enabled = record.mediaUri != null,
                        ) { Text(stringResource(R.string.history_open)) }
                        TextButton(
                            onClick = {
                                MediaIntents.shareIntent(record.mediaUri, record.mode)
                                    ?.let { context.startActivity(it) }
                            },
                            enabled = record.mediaUri != null,
                        ) { Text(stringResource(R.string.history_share)) }
                    }
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
    val shape = MaterialTheme.shapes.large
    // Prefer the platform's thumbnail; when there is none (Instagram routinely returns no thumbnail
    // URL), fall back to a frame decoded from the saved video itself, so the card is not just a
    // placeholder for a file that is sitting right there on the device.
    val model =
        record.thumbnailUrl
            ?: record.mediaUri?.takeIf { record.status == DownloadStatus.COMPLETED && record.mode == DownloadMode.VIDEO }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = Sizes.thumbnailWidth, height = Sizes.thumbnailHeight).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(width = Sizes.thumbnailWidth, height = Sizes.thumbnailHeight).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            VideoPlaceholder(
                modifier = Modifier.size(Sizes.thumbnailGlyph),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusLabel(record: DownloadRecord): String =
    when (record.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.history_status_queued)
        DownloadStatus.RUNNING -> stringResource(R.string.history_status_running)
        DownloadStatus.COMPLETED ->
            stringResource(R.string.history_status_saved) +
                (record.sizeBytes?.let { " · ${formatSize(it)}" } ?: "") +
                if (record.mode == DownloadMode.AUDIO) " · ${stringResource(R.string.history_status_audio)}" else ""
        DownloadStatus.FAILED -> stringResource(R.string.history_status_failed)
        DownloadStatus.CANCELLED -> stringResource(R.string.history_status_cancelled)
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
