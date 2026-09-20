package io.celox.flipperripper.ui

import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.usecase.ResolveUrlUseCase
import io.celox.flipperripper.domain.usecase.StartDownloadUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes a link shared into the app and turns it into a download.
 *
 * **Why this is application-scoped and not a ViewModel.** It used to live in `HomeViewModel`, which
 * `hiltViewModel()` creates only when the Home screen composes. After the system reclaimed the
 * process, the NavHost restored whichever tab was last open — and if that was History or Settings,
 * Home never composed, the ViewModel was never built, nobody consumed the link bus, and the shared
 * link was **silently dropped**: the app opened, nothing downloaded, no error anywhere. Measured and
 * reproduced 5 times out of 5, and it is the reason the behaviour looked random rather than broken:
 * it depended on which tab you had used last.
 *
 * Living on the application scope also takes the ViewModel's construction off the critical path —
 * the consumer is alive from process start, so a share no longer waits for the first composition.
 */
@Singleton
class ShareLinkHandler
@Inject
constructor(
    incomingLinkBus: IncomingLinkBus,
    settingsRepository: SettingsRepository,
    private val appNavigator: AppNavigator,
    private val startDownload: StartDownloadUseCase,
    private val resolveUrl: ResolveUrlUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * Started eagerly, so the first DataStore read overlaps with app start-up instead of landing in
     * the middle of a share. The initial value is deliberately `null` rather than
     * [UserPreferences]: a placeholder default says auto-download is **on**, and handing that out
     * before the stored value arrives would download for someone who had switched it off.
     */
    private val preferences: StateFlow<UserPreferences?> =
        settingsRepository.preferences.stateIn(scope, SharingStarted.Eagerly, null)

    private val _prefilledLink = MutableStateFlow<String?>(null)

    /**
     * A shared link waiting for the Home screen, when auto-download is off. A [StateFlow] rather
     * than an event: it holds the link until Home actually reads it, so the link cannot be lost
     * again the way the one-shot channel lost it.
     */
    val prefilledLink: StateFlow<String?> = _prefilledLink.asStateFlow()

    init {
        scope.launch { incomingLinkBus.links.collect { handle(it) } }
    }

    fun consumePrefilledLink() {
        _prefilledLink.value = null
    }

    private suspend fun handle(text: String) {
        val parsed = resolveUrl(text) ?: return
        val prefs = preferences.filterNotNull().first()

        if (!prefs.autoDownloadOnShare) {
            // Nothing is downloading, so the prefilled form is the right screen — and it has to be
            // brought forward, or a share landing on another tab resolves invisibly.
            _prefilledLink.value = parsed.url
            appNavigator.navigateTo(AppNavTarget.HOME)
            return
        }

        // History first, then enqueue: the record appears on a screen the user is already looking
        // at, instead of the app sitting on the start screen until the database and WorkManager are
        // done. The card's own phase takes over from there.
        appNavigator.navigateTo(AppNavTarget.HISTORY)
        startDownload(
            DownloadRequest(
                url = parsed.url,
                platform = parsed.platform,
                quality = prefs.defaultQuality,
            ),
        )
    }
}
