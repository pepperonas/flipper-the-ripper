package io.celox.flipperripper.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.ui.components.SegmentedToggle
import io.celox.flipperripper.ui.components.springPressed
import io.celox.flipperripper.ui.util.ObserveAsEvents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val backendConfig by viewModel.backendConfig.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedServerMsg = stringResource(R.string.settings_server_saved)

    ObserveAsEvents(viewModel.messages) { message -> snackbarHostState.showSnackbar(message) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                SegmentedToggle(
                    options =
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ),
                    selected = prefs.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = prefs.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SettingsSection(stringResource(R.string.settings_behavior)) {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_download),
                    subtitle = stringResource(R.string.settings_auto_download_desc),
                    checked = prefs.autoDownloadOnShare,
                    onCheckedChange = viewModel::setAutoDownload,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_clipboard),
                    subtitle = stringResource(R.string.settings_clipboard_desc),
                    checked = prefs.clipboardDetection,
                    onCheckedChange = viewModel::setClipboardDetection,
                )
            }

            SettingsSection(stringResource(R.string.settings_source_title)) {
                DownloadSourceSection(
                    config = backendConfig,
                    onSource = viewModel::setDownloadSource,
                    onSaveServer = { url, key ->
                        viewModel.setServer(url, key)
                        scope.launch { snackbarHostState.showSnackbar(savedServerMsg) }
                    },
                )
            }

            SettingsSection(stringResource(R.string.settings_engine)) {
                Text(
                    stringResource(R.string.settings_update_engine_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val interaction = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = viewModel::updateEngineNow,
                    interactionSource = interaction,
                    modifier = Modifier.height(52.dp).springPressed(interaction),
                ) { Text(stringResource(R.string.settings_update_engine)) }
            }

            SettingsSection(stringResource(R.string.settings_about)) {
                Text(
                    "${stringResource(R.string.settings_version)}: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_legal), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_legal_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
    )
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DownloadSourceSection(
    config: io.celox.flipperripper.domain.model.BackendConfig,
    onSource: (io.celox.flipperripper.domain.model.DownloadSource) -> Unit,
    onSaveServer: (String, String) -> Unit,
) {
    Text(
        stringResource(R.string.settings_source_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SegmentedToggle(
        options =
        listOf(
            io.celox.flipperripper.domain.model.DownloadSource.ON_DEVICE to
                stringResource(R.string.settings_source_ondevice),
            io.celox.flipperripper.domain.model.DownloadSource.SERVER to
                stringResource(R.string.settings_source_server),
        ),
        selected = config.source,
        onSelect = onSource,
    )

    if (config.source == io.celox.flipperripper.domain.model.DownloadSource.SERVER) {
        var url by rememberSaveable(config.url) { mutableStateOf(config.url) }
        var key by rememberSaveable(config.apiKey) { mutableStateOf(config.apiKey) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.settings_server_url)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.settings_server_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val interaction = remember { MutableInteractionSource() }
        FilledTonalButton(
            onClick = { onSaveServer(url, key) },
            interactionSource = interaction,
            enabled = url.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.height(52.dp).springPressed(interaction),
        ) { Text(stringResource(R.string.settings_server_save)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
