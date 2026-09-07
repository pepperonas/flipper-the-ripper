package io.celox.flipperripper.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.celox.flipperripper.data.update.UpdateCoordinator
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.ui.theme.FlipperTheme
import io.celox.flipperripper.ui.theme.isDarkTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var incomingLinkBus: IncomingLinkBus

    @Inject lateinit var appNavigator: AppNavigator

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var updateCoordinator: UpdateCoordinator

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a FRESH launch may consume the launch intent. After a recreation (config change,
        // process restore) getIntent() still returns the old share intent — re-posting it enqueued
        // the same download a second time.
        if (savedInstanceState == null) handleIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
            // System-bar icons follow the app's theme choice, not the OS: forced Light on a dark OS
            // otherwise leaves white icons on the light surface (and vice versa).
            val dark = isDarkTheme(prefs.themeMode)
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            FlipperTheme(themeMode = prefs.themeMode, dynamicColor = prefs.useDynamicColor) {
                FlipperApp(appNavigator)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Forward a shared text link to the Home ViewModel via the bus. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { incomingLinkBus.post(it) }
            // A link is about to hit the extractor — the freshest moment to make sure yt-dlp and
            // the app itself are current. Throttled + best-effort inside the coordinator.
            updateCoordinator.runChecksAsync()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
