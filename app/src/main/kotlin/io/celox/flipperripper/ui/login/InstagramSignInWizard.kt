package io.celox.flipperripper.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.flipperripper.R
import io.celox.flipperripper.ui.motion.rememberReduceMotion
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing

/**
 * The sign-in wizard a failed Instagram download offers: why → Instagram's own login page → done.
 *
 * Step one says what signing in changes and, before anything is typed, what happens to the password
 * (it stays on Instagram's page). Step two is the same login page Settings uses. Step three confirms,
 * and — when the wizard was opened from a failed download — that download is already running again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramSignInWizard(onClose: () -> Unit, viewModel: InstagramSignInViewModel = hiltViewModel()) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ig_wizard_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onFinished(onClose) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ig_wizard_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StepIndicator(step)
            AnimatedContent(
                targetState = step,
                label = "wizard-step",
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn() + slideInHorizontally { it / WIZARD_SLIDE_DIVISOR }) togetherWith fadeOut()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    SignInStep.INTRO ->
                        IntroStep(onContinue = viewModel::onContinue, onNotNow = { viewModel.onFinished(onClose) })
                    SignInStep.LOGIN ->
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                stringResource(R.string.ig_wizard_login_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                            )
                            InstagramLoginPage(onSignedIn = viewModel::onSignedIn, modifier = Modifier.fillMaxSize())
                        }
                    SignInStep.DONE ->
                        DoneStep(retrying = viewModel.retryId != null, onFinish = { viewModel.onFinished(onClose) })
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: SignInStep) {
    val index = step.ordinal
    val total = SignInStep.entries.size
    val label = stringResource(R.string.ig_wizard_step, index + 1, total)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm)
            .semantics { contentDescription = label },
    ) {
        SignInStep.entries.forEach { s ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (s.ordinal <= index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntroStep(onContinue: () -> Unit, onNotNow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.ig_wizard_intro_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Fact(Icons.Outlined.VisibilityOff, stringResource(R.string.ig_wizard_intro_why))
        Fact(Icons.Outlined.Lock, stringResource(R.string.ig_wizard_intro_password))
        Fact(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.ig_wizard_intro_signout))
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(Sizes.primaryButtonHeight)) {
            Text(stringResource(R.string.ig_wizard_continue))
        }
        TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ig_wizard_not_now))
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DoneStep(retrying: Boolean, onFinish: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.emptyMotif),
        )
        Text(stringResource(R.string.ig_wizard_done_title), style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            stringResource(if (retrying) R.string.ig_wizard_done_retry else R.string.ig_wizard_done_home),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(Sizes.primaryButtonHeight)) {
            Text(stringResource(if (retrying) R.string.ig_wizard_show_downloads else R.string.ig_wizard_back))
        }
    }
}

/** The next step slides in a sixth of the width — direction, not a full page swipe. */
private const val WIZARD_SLIDE_DIVISOR = 6
