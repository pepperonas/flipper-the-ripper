package io.celox.flipperripper.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.ui.components.PlatformBadge
import io.celox.flipperripper.ui.util.ObserveAsEvents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDownloadStarted: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.DownloadStarted -> onDownloadStarted()
            is HomeEvent.ShowMessage ->
                scope.launch { snackbarHostState.showSnackbar(event.message) }
        }
    }

    // Offer clipboard link once the screen is shown, if enabled.
    androidx.compose.runtime.LaunchedEffect(state.clipboardDetectionEnabled) {
        viewModel.checkClipboard(state.clipboardDetectionEnabled)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            if (!state.engineReady) {
                EngineBanner()
                Spacer(Modifier.height(12.dp))
            }

            state.clipboardSuggestion?.let { suggestion ->
                ClipboardSuggestionCard(
                    url = suggestion.url,
                    onUse = { viewModel.acceptClipboardSuggestion() },
                    onDismiss = { viewModel.dismissClipboardSuggestion() },
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.urlInput,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.home_url_label)) },
                placeholder = { Text(stringResource(R.string.home_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { viewModel.onPaste(readClipboard(context)) }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.home_paste))
                        }
                        if (state.urlInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onUrlChange("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.home_clear))
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            state.detectedPlatform?.let {
                Spacer(Modifier.height(8.dp))
                PlatformBadge(platform = it)
            }

            Spacer(Modifier.height(16.dp))
            ActionRow(
                canDownload = state.canDownload,
                isResolving = state.isResolving,
                onResolve = viewModel::resolve,
                onDownload = { viewModel.download(state.defaultMode) },
            )

            if (state.showAudioOption) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.download(DownloadMode.AUDIO) },
                    enabled = state.canDownload,
                ) { Text(stringResource(R.string.home_download_audio)) }
            }

            if (state.isResolving) {
                Spacer(Modifier.height(24.dp))
                ResolvingIndicator()
            }

            state.videoInfo?.let { info ->
                Spacer(Modifier.height(20.dp))
                VideoPreview(
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                )
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                ErrorCard(message)
            }
            Spacer(Modifier.height(24.dp))
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
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onResolve,
            enabled = canDownload && !isResolving,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.home_fetch)) }
        Button(
            onClick = onDownload,
            enabled = canDownload,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.home_download)) }
    }
}

@Composable
private fun EngineBanner() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.home_engine_initializing))
        }
    }
}

@Composable
private fun ClipboardSuggestionCard(url: String, onUse: () -> Unit, onDismiss: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_clipboard_prompt), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUse) { Text(stringResource(R.string.home_clipboard_use)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dismiss)) }
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
        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.home_resolving))
    }
}

@Composable
private fun VideoPreview(title: String, uploader: String?, thumbnailUrl: String?) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                uploader?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
