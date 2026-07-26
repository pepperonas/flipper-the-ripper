package io.celox.flipperripper.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.ui.util.ObserveAsEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.messages) { message ->
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_appearance))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            ThemeOption(stringResource(R.string.settings_theme_system), prefs.themeMode == ThemeMode.SYSTEM) {
                viewModel.setThemeMode(ThemeMode.SYSTEM)
            }
            ThemeOption(stringResource(R.string.settings_theme_light), prefs.themeMode == ThemeMode.LIGHT) {
                viewModel.setThemeMode(ThemeMode.LIGHT)
            }
            ThemeOption(stringResource(R.string.settings_theme_dark), prefs.themeMode == ThemeMode.DARK) {
                viewModel.setThemeMode(ThemeMode.DARK)
            }
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_desc),
                checked = prefs.useDynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_behavior))
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

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_engine))
            Text(stringResource(R.string.settings_update_engine_desc), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = viewModel::updateEngineNow) {
                Text(stringResource(R.string.settings_update_engine))
            }

            SectionDivider()
            SectionTitle(stringResource(R.string.settings_about))
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
    Divider()
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.height(0.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
