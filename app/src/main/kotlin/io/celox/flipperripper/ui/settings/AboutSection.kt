package io.celox.flipperripper.ui.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.R
import io.celox.flipperripper.ui.components.AppMark
import io.celox.flipperripper.ui.components.springPressed
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing
import io.celox.flipperripper.ui.util.openUrl

/**
 * About: who made the app, where it lives, under which licence — and a way to say thanks. Every fact
 * comes from [AboutLinks] or [BuildConfig]; nothing here is typed by hand.
 *
 * @param onOpenFailed invoked when no app on the device can open a link (the caller shows a snackbar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSection(onOpenFailed: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        if (!context.openUrl(url)) onOpenFailed()
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        AppMark(modifier = Modifier.size(Sizes.aboutMotif))
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(Spacing.lg))
    Text(stringResource(R.string.about_made_by, AboutLinks.AUTHOR), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(Spacing.xs))
    Text(
        stringResource(R.string.about_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LinkChip(AboutLinks.WEBSITE_LABEL, Icons.Outlined.Language) { open(AboutLinks.WEBSITE_URL) }
        LinkChip(stringResource(R.string.about_source), Icons.Outlined.Code) { open(AboutLinks.REPO_URL) }
        LinkChip(stringResource(R.string.about_license, AboutLinks.LICENSE_NAME), Icons.Outlined.Description) {
            open(AboutLinks.LICENSE_URL)
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        stringResource(R.string.about_license_hint, AboutLinks.LICENSE_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.lg))
    val donateInteraction = remember { MutableInteractionSource() }
    Button(
        onClick = { open(AboutLinks.donateUrl()) },
        interactionSource = donateInteraction,
        colors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().height(Sizes.buttonHeight).springPressed(donateInteraction),
    ) {
        Icon(Icons.Outlined.Favorite, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.about_donate))
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        stringResource(R.string.about_donate_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LinkChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        // 40 dp is a comfortable target for a chip while staying visually light; the row's spacing
        // keeps neighbouring chips from touching.
        modifier = Modifier.height(40.dp),
    )
}
