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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.celox.flipperripper.domain.model.QueueOrdering
import io.celox.flipperripper.domain.model.isActive
import io.celox.flipperripper.domain.model.isPausable
import io.celox.flipperripper.domain.model.isPending
import io.celox.flipperripper.domain.model.isReorderable
import io.celox.flipperripper.ui.components.DragHandleMark
import io.celox.flipperripper.ui.components.EmptyDownloadsMark
import io.celox.flipperripper.ui.components.ExpressiveLoadingIndicator
import io.celox.flipperripper.ui.components.VideoPlaceholder
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing
import io.celox.flipperripper.util.MediaIntents
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val records by viewModel.history.collectAsStateWithLifecycle()
    // Clearing the history cannot be undone, so the button only *asks*; nothing is removed until the
    // dialog is confirmed. Survives rotation, so a config change can never silently drop the question.
    var askClearAll by rememberSaveable { mutableStateOf(false) }

    if (askClearAll) {
        ClearHistoryDialog(
            entryCount = records?.size ?: 0,
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
                    if (!records.isNullOrEmpty()) {
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
        val loaded = records
        when {
            // Not the same as "empty": the database has not answered yet. Drawing the empty state
            // here told anyone who had just shared a link that nothing had been downloaded.
            loaded == null -> LoadingState(Modifier.fillMaxSize().padding(padding))
            loaded.isEmpty() -> EmptyState(Modifier.fillMaxSize().padding(padding))
            else -> DownloadList(loaded, Modifier.fillMaxSize().padding(padding), viewModel)
        }
    }
}

/**
 * The queue on top, the finished downloads below.
 *
 * The split is [isPending], not [isActive]: a paused download shows no spinner but still belongs
 * with the queue, because it is going to run.
 */
@Composable
private fun DownloadList(records: List<DownloadRecord>, modifier: Modifier, viewModel: HistoryViewModel) {
    val queue = records.filter { it.status.isPending }.sortedWith(compareBy({ it.queueOrder }, { it.id }))
    val finished = records.filter { !it.status.isPending }

    // While a card is under the finger the list must follow the finger, not the database — the drag
    // is only written when it is let go. `draft` holds that intermediate order and is dropped again
    // as soon as the database agrees (or the set of queued downloads changes underneath it).
    var draft by remember { mutableStateOf<List<String>?>(null) }
    val queueIds = queue.map { it.id }
    LaunchedEffect(queueIds) {
        val pending = draft
        if (pending != null && (pending.toSet() != queueIds.toSet() || pending == queueIds)) draft = null
    }
    val shown = draft?.mapNotNull { id -> queue.firstOrNull { it.id == id } } ?: queue

    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            // Keys, not indices: the list has section headers, so a raw lazy-list index is not a
            // position in the queue, and translating between the two by hand is exactly the
            // off-by-one this avoids.
            val fromId = from.key as? String ?: return@rememberReorderableLazyListState
            val toId = to.key as? String ?: return@rememberReorderableLazyListState
            val current = draft ?: queueIds
            val fromIndex = current.indexOf(fromId)
            val toIndex = current.indexOf(toId)
            if (fromIndex >= 0 && toIndex >= 0) draft = QueueOrdering.move(current, fromIndex, toIndex)
        }

    // A newly enqueued download joins the *back* of the queue, so the old "scroll to the newest"
    // rule no longer points at it. Showing the head of the queue is the useful answer: that is what
    // is running now.
    LaunchedEffect(queueIds.size) { if (queueIds.isNotEmpty()) listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (shown.isNotEmpty()) {
            item(key = KEY_HEADER_ACTIVE) { SectionHeader(stringResource(R.string.history_section_active)) }
            items(shown, key = { it.id }) { record ->
                ReorderableItem(reorderState, key = record.id) { _ ->
                    DownloadCard(
                        record = record,
                        onCancel = { viewModel.cancel(record.id) },
                        onRetry = { viewModel.retry(record.id) },
                        onDelete = { viewModel.delete(record.id) },
                        onPause = { viewModel.pause(record.id) },
                        onResume = { viewModel.resume(record.id) },
                        dragHandle =
                        if (record.status.isReorderable) {
                            {
                                val description = stringResource(R.string.history_reorder)
                                Box(
                                    modifier =
                                    Modifier
                                        .size(Sizes.touchTarget)
                                        .draggableHandle(
                                            onDragStopped = { draft?.let(viewModel::reorder) },
                                        )
                                        .semantics { contentDescription = description },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    DragHandleMark(
                                        modifier = Modifier.size(Sizes.dragHandle),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (finished.isNotEmpty()) {
            item(key = KEY_HEADER_DONE) { SectionHeader(stringResource(R.string.history_section_done)) }
            items(finished, key = { it.id }) { record ->
                // Swipe clears a finished entry without hunting for a button. Only finished ones:
                // swiping away something that is still downloading would be an accident waiting to
                // happen, and the card carries Cancel for that.
                val dismissState = rememberSwipeToDismissBoxState()
                // Reacting to the settled value rather than vetoing the change: `confirmValueChange`
                // is deprecated, and it was the wrong shape anyway — the swipe is not a question to
                // approve, the card simply leaves. It disappears from the list on its own once the
                // row is gone, so there is no state to reset.
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        viewModel.delete(record.id)
                    }
                }
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = { SwipeBackground() },
                    modifier = Modifier.animateItem(),
                ) {
                    DownloadCard(
                        record = record,
                        onCancel = { viewModel.cancel(record.id) },
                        onRetry = { viewModel.retry(record.id) },
                        onDelete = { viewModel.delete(record.id) },
                        onPause = { viewModel.pause(record.id) },
                        onResume = { viewModel.resume(record.id) },
                        dragHandle = null,
                    )
                }
            }
        }
    }
}

private const val KEY_HEADER_ACTIVE = "header_active"
private const val KEY_HEADER_DONE = "header_done"

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
    )
}

@Composable
private fun SwipeBackground() {
    Box(
        modifier = Modifier.fillMaxWidth().height(Sizes.swipeBackground).clip(MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Outlined.DeleteSweep,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
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
private fun LoadingState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveLoadingIndicator(modifier = Modifier.size(Sizes.inlineIndicator))
    }
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
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    dragHandle: (@Composable () -> Unit)?,
) {
    val context = LocalContext.current
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
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
                // Every in-flight phase, not only RUNNING. Gating this on one phase is what made
                // the indicator blink out while metadata resolved and again while the file was
                // being saved — exactly the moments the user needed to see that work continued.
                if (record.status.isActive) {
                    ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
                }
                dragHandle?.invoke()
            }

            if (record.status.isActive) {
                Spacer(Modifier.height(Spacing.md))
                val percent = record.progressPercent
                // Determinate only while bytes are moving and a total is known — the same rule the
                // notification follows, from the same field, so the two cannot show different
                // numbers. Preparing and post-processing have nothing honest to measure.
                if (record.status == DownloadStatus.RUNNING && percent != null) {
                    LinearWavyProgressIndicator(
                        progress = { (percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            record.errorMessage?.takeIf { record.status == DownloadStatus.FAILED }?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CardActions(record, context, onCancel, onRetry, onPause, onResume)
                Box(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.history_delete)) }
            }
        }
    }
}

@Composable
private fun CardActions(
    record: DownloadRecord,
    context: android.content.Context,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    when (record.status) {
        DownloadStatus.COMPLETED -> {
            TextButton(
                onClick = {
                    MediaIntents.viewIntent(record.mediaUri, record.mode)?.let { context.startActivity(it) }
                },
                enabled = record.mediaUri != null,
            ) { Text(stringResource(R.string.history_open)) }
            TextButton(
                onClick = {
                    MediaIntents.shareIntent(record.mediaUri, record.mode)?.let { context.startActivity(it) }
                },
                enabled = record.mediaUri != null,
            ) { Text(stringResource(R.string.history_share)) }
        }
        DownloadStatus.PAUSED ->
            TextButton(onClick = onResume) { Text(stringResource(R.string.history_resume)) }
        DownloadStatus.FAILED, DownloadStatus.CANCELLED ->
            TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
        else -> Unit
    }
    // Pause sits beside Cancel rather than replacing it: stopping for now and giving up are
    // different intentions, and PROCESSING deliberately offers neither pause (nothing to resume
    // from) nor a missing Cancel.
    if (record.status.isPausable) {
        TextButton(onClick = onPause) { Text(stringResource(R.string.history_pause)) }
    }
    if (record.status.isPending) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.history_cancel)) }
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
        DownloadStatus.PREPARING -> stringResource(R.string.history_status_preparing)
        DownloadStatus.RUNNING ->
            stringResource(R.string.history_status_running) +
                (record.progressPercent?.let { " · ${it.toInt().coerceIn(0, 100)} %" } ?: "")
        DownloadStatus.PROCESSING -> stringResource(R.string.history_status_processing)
        DownloadStatus.PAUSED ->
            stringResource(R.string.history_status_paused) +
                (record.progressPercent?.let { " · ${it.toInt().coerceIn(0, 100)} %" } ?: "")
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
