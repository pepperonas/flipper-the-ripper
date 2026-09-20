package io.celox.flipperripper.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.celox.flipperripper.R
import io.celox.flipperripper.domain.model.QualityChoice
import io.celox.flipperripper.ui.theme.Spacing

/**
 * The one place a download is configured.
 *
 * Every capability the app gains lives here as an *offer beside* the default path, never as a
 * question before it: the leading half of the split button downloads with what is already chosen,
 * and this sheet is what the chevron opens. Steps C and D add sections to it; the vocabulary is
 * meant to hold them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    available: Set<QualityChoice>,
    selected: QualityChoice,
    bestHeight: Int?,
    onSelect: (QualityChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxxl)) {
            Text(
                stringResource(R.string.quality_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                // What a platform cannot do is explained, not silently missing: an empty group
                // reads as a broken screen.
                if (available.size == 1) {
                    stringResource(R.string.quality_single_file)
                } else {
                    bestHeight?.let { stringResource(R.string.quality_up_to, it) }
                        ?: stringResource(R.string.quality_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            QualityOptions(available = available, selected = selected, onSelect = onSelect)
        }
    }
}

/**
 * The tiers, as a wrapping row of toggle buttons.
 *
 * Wrapping rather than a fixed row: five labels do not fit across a 320 dp screen, and a group that
 * squeezes its own text is worse than one that takes two lines.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QualityOptions(
    available: Set<QualityChoice>,
    selected: QualityChoice,
    onSelect: (QualityChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Quality" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        QualityChoice.entries.forEach { choice ->
            val offered = choice in available
            ToggleButton(
                checked = choice == selected,
                onCheckedChange = { if (offered) onSelect(choice) },
                enabled = offered,
            ) { Text(qualityLabel(choice)) }
        }
    }
}

/** The label for a tier. `Best` and `Audio only` are words; the rest are the resolutions. */
@Composable
fun qualityLabel(choice: QualityChoice): String =
    when (choice) {
        QualityChoice.BEST -> stringResource(R.string.quality_best)
        QualityChoice.AUDIO_ONLY -> stringResource(R.string.quality_audio_only)
        else -> stringResource(R.string.quality_p, choice.maxHeight ?: 0)
    }
