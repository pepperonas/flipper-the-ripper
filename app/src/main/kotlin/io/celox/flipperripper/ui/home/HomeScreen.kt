package io.celox.flipperripper.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.ui.components.AppMark
import io.celox.flipperripper.ui.components.ExpressiveLoadingIndicator
import io.celox.flipperripper.ui.components.PlatformBadge
import io.celox.flipperripper.ui.components.SegmentedToggle
import io.celox.flipperripper.ui.components.springPressed
import io.celox.flipperripper.ui.motion.fadeRiseIn
import io.celox.flipperripper.ui.theme.FieldShape
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing
import io.celox.flipperripper.ui.util.ObserveAsEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mode by remember(state.detectedPlatform) { mutableStateOf(state.defaultMode) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
        }
    }

    LaunchedEffect(state.clipboardDetectionEnabled) {
        viewModel.checkClipboard(state.clipboardDetectionEnabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xl)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Spacing.sm))
            Hero()
            Spacer(Modifier.height(Spacing.xl))

            AnimatedVisibility(
                visible = state.updateNotice != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
            ) {
                state.updateNotice?.let { update ->
                    Column {
                        UpdateNoticeCard(
                            version = update.version,
                            onGet = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(update.url),
                                        ),
                                    )
                                }
                            },
                            onDismiss = { viewModel.dismissUpdateNotice() },
                        )
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }

            AnimatedVisibility(visible = !state.engineReady) {
                Column {
                    EngineBanner()
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            AnimatedVisibility(
                visible = state.clipboardSuggestion != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
            ) {
                state.clipboardSuggestion?.let { suggestion ->
                    Column {
                        ClipboardSuggestionCard(
                            url = suggestion.url,
                            onUse = { viewModel.acceptClipboardSuggestion() },
                            onDismiss = { viewModel.dismissClipboardSuggestion() },
                        )
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }

            OutlinedTextField(
                value = state.urlInput,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.home_url_label)) },
                placeholder = { Text(stringResource(R.string.home_url_hint)) },
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { viewModel.onPaste(readClipboard(context)) }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.home_paste))
                        }
                        AnimatedVisibility(visible = state.urlInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onUrlChange("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.home_clear))
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = state.detectedPlatform != null) {
                state.detectedPlatform?.let {
                    Column {
                        Spacer(Modifier.height(Spacing.md))
                        PlatformBadge(platform = it)
                    }
                }
            }

            AnimatedVisibility(visible = state.showAudioOption) {
                Column {
                    Spacer(Modifier.height(Spacing.lg))
                    SegmentedToggle(
                        options =
                        listOf(
                            DownloadMode.VIDEO to stringResource(R.string.home_mode_video),
                            DownloadMode.AUDIO to stringResource(R.string.home_mode_audio),
                        ),
                        selected = mode,
                        onSelect = { mode = it },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            ActionRow(
                canDownload = state.canDownload,
                isResolving = state.isResolving,
                onResolve = viewModel::resolve,
                onDownload = { viewModel.download(mode) },
            )

            AnimatedVisibility(visible = state.isResolving) {
                Column {
                    Spacer(Modifier.height(Spacing.xxl))
                    ResolvingIndicator()
                }
            }

            state.videoInfo?.let { info ->
                Spacer(Modifier.height(Spacing.xxl))
                VideoPreview(
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                )
            }

            AnimatedVisibility(visible = state.errorMessage != null) {
                state.errorMessage?.let { message ->
                    Column {
                        Spacer(Modifier.height(Spacing.lg))
                        ErrorCard(message)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
private fun Hero() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        AppMark(modifier = Modifier.size(Sizes.heroMotif))
        Column {
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_url_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    canDownload: Boolean,
    isResolving: Boolean,
    onResolve: () -> Unit,
    onDownload: () -> Unit,
) {
    val resolveInteraction = remember { MutableInteractionSource() }
    val downloadInteraction = remember { MutableInteractionSource() }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onResolve,
            enabled = canDownload && !isResolving,
            interactionSource = resolveInteraction,
            modifier = Modifier.weight(1f).height(Sizes.primaryButtonHeight).springPressed(resolveInteraction),
        ) { Text(stringResource(R.string.home_fetch)) }
        Button(
            onClick = onDownload,
            enabled = canDownload,
            interactionSource = downloadInteraction,
            modifier = Modifier.weight(1f).height(Sizes.primaryButtonHeight).springPressed(downloadInteraction),
        ) { Text(stringResource(R.string.home_download)) }
    }
}

@Composable
private fun EngineBanner() {
    Card(
        shape = FieldShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            ExpressiveLoadingIndicator(modifier = Modifier.size(Sizes.inlineIndicator))
            Spacer(Modifier.width(Spacing.lg))
            Text(
                stringResource(R.string.home_engine_initializing),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun UpdateNoticeCard(version: String, onGet: () -> Unit, onDismiss: () -> Unit) {
    val getInteraction = remember { MutableInteractionSource() }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(
                stringResource(R.string.home_update_available, version),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.home_update_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onGet,
                    interactionSource = getInteraction,
                    modifier = Modifier.springPressed(getInteraction),
                ) { Text(stringResource(R.string.home_update_get)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.home_dismiss))
                }
            }
        }
    }
}

@Composable
private fun ClipboardSuggestionCard(url: String, onUse: () -> Unit, onDismiss: () -> Unit) {
    val useInteraction = remember { MutableInteractionSource() }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(
                stringResource(R.string.home_clipboard_prompt),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onUse,
                    interactionSource = useInteraction,
                    modifier = Modifier.springPressed(useInteraction),
                ) { Text(stringResource(R.string.home_clipboard_use)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.home_dismiss))
                }
            }
        }
    }
}

@Composable
private fun ResolvingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveLoadingIndicator(modifier = Modifier.size(Sizes.inlineIndicator))
        Spacer(Modifier.width(Spacing.md))
        Text(stringResource(R.string.home_resolving), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun VideoPreview(title: String, uploader: String?, thumbnailUrl: String?) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().fadeRiseIn(),
    ) {
        Column {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(Sizes.previewImageHeight),
                )
            }
            Column(Modifier.padding(Spacing.xl)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 3)
                uploader?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = FieldShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().fadeRiseIn(),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
