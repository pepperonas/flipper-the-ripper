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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.EngineUpdateOutcome
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.ui.components.ExpressiveLoadingIndicator
import io.celox.flipperripper.ui.components.SegmentedToggle
import io.celox.flipperripper.ui.components.springPressed
import io.celox.flipperripper.ui.theme.FieldShape
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing
import io.celox.flipperripper.ui.util.ObserveAsEvents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenInstagramLogin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val backendConfig by viewModel.backendConfig.collectAsStateWithLifecycle()
    val instagramLoggedIn by viewModel.instagramLoggedIn.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedServerMsg = stringResource(R.string.settings_server_saved)
    val openFailedMsg = stringResource(R.string.about_open_failed)
    val engineUpdatedMsg = stringResource(R.string.settings_engine_updated)
    val engineCurrentMsg = stringResource(R.string.settings_engine_already_current)
    val engineUnknownFmt = stringResource(R.string.settings_engine_update_unknown)
    val updatingEngine by viewModel.updatingEngine.collectAsStateWithLifecycle()

    // Re-read the Instagram login state whenever Settings is shown, so it reflects a just-completed
    // (or cleared) login when the login screen pops back here.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshInstagram() }

    ObserveAsEvents(viewModel.messages) { message ->
        val text =
            when (message) {
                is SettingsMessage.Plain -> message.text
                is SettingsMessage.EngineUpdate ->
                    when (val outcome = message.outcome) {
                        EngineUpdateOutcome.Updated -> engineUpdatedMsg
                        EngineUpdateOutcome.AlreadyCurrent -> engineCurrentMsg
                        is EngineUpdateOutcome.Unrecognised -> String.format(engineUnknownFmt, outcome.raw)
                    }
            }
        snackbarHostState.showSnackbar(text)
    }

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
                .padding(Spacing.xl),
        ) {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
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
                Spacer(Modifier.height(Spacing.sm))
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

            SettingsSection(stringResource(R.string.settings_instagram_title)) {
                Text(
                    stringResource(R.string.settings_instagram_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                val igInteraction = remember { MutableInteractionSource() }
                if (instagramLoggedIn) {
                    Text(
                        stringResource(R.string.settings_instagram_signed_in),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    FilledTonalButton(
                        onClick = viewModel::signOutInstagram,
                        interactionSource = igInteraction,
                        modifier = Modifier.height(Sizes.buttonHeight).springPressed(igInteraction),
                    ) { Text(stringResource(R.string.settings_instagram_sign_out)) }
                } else {
                    FilledTonalButton(
                        onClick = onOpenInstagramLogin,
                        interactionSource = igInteraction,
                        modifier = Modifier.height(Sizes.buttonHeight).springPressed(igInteraction),
                    ) { Text(stringResource(R.string.settings_instagram_sign_in)) }
                }
            }

            SettingsSection(stringResource(R.string.settings_engine)) {
                Text(
                    stringResource(R.string.settings_update_engine_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                val interaction = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = viewModel::updateEngineNow,
                    enabled = !updatingEngine,
                    interactionSource = interaction,
                    modifier = Modifier.height(Sizes.buttonHeight).springPressed(interaction),
                ) {
                    // Fetching the engine takes seconds over the network; an unchanged button reads as
                    // "nothing happened" and invites a second tap.
                    if (updatingEngine) {
                        ExpressiveLoadingIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.settings_update_engine_running))
                    } else {
                        Text(stringResource(R.string.settings_update_engine))
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_about)) {
                AboutSection(onOpenFailed = { scope.launch { snackbarHostState.showSnackbar(openFailedMsg) } })
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(R.string.settings_legal), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.xs))
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
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md),
    )
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl)) { content() }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.padding(top = Spacing.xs))
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
    Spacer(Modifier.height(Spacing.md))
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
        Spacer(Modifier.height(Spacing.md))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.settings_server_url)) },
            singleLine = true,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.settings_server_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        val interaction = remember { MutableInteractionSource() }
        FilledTonalButton(
            onClick = { onSaveServer(url, key) },
            interactionSource = interaction,
            enabled = url.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.height(Sizes.buttonHeight).springPressed(interaction),
        ) { Text(stringResource(R.string.settings_server_save)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
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
