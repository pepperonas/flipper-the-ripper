package io.celox.flipperripper.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.data.engine.InstagramSession
import io.celox.flipperripper.domain.usecase.RetryDownloadUseCase
import io.celox.flipperripper.ui.AppNavTarget
import io.celox.flipperripper.ui.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three steps of the Instagram sign-in wizard. */
enum class SignInStep { INTRO, LOGIN, DONE }

/**
 * Drives the sign-in wizard that a failed Instagram download offers.
 *
 * Opened from a failed download ([retryId] = that download) or from Home's *Load info* (no id). Once
 * Instagram has set its session cookie the wizard says so and — the part that matters — starts the
 * failed download again by itself; the user never has to find the card and press Retry. From Home,
 * Home notices the new session and loads the info again (HomeViewModel).
 */
@HiltViewModel
class InstagramSignInViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val session: InstagramSession,
    private val retryDownload: RetryDownloadUseCase,
    private val appNavigator: AppNavigator,
) : ViewModel() {
    /** The download to restart after signing in, or null when the wizard came from Home. */
    val retryId: String? = savedStateHandle.get<String>(ARG_RETRY)?.takeIf { it.isNotBlank() }

    private val _step = MutableStateFlow(SignInStep.INTRO)
    val step: StateFlow<SignInStep> = _step.asStateFlow()

    init {
        // Signed in meanwhile (another tab, an earlier attempt): nothing to explain, only to finish.
        session.refresh()
        if (session.loggedIn.value) finishSignIn()
    }

    fun onContinue() {
        if (_step.value == SignInStep.INTRO) _step.value = SignInStep.LOGIN
    }

    /** Called by the login page as soon as Instagram's session cookie exists. */
    fun onSignedIn() {
        session.refresh()
        if (session.loggedIn.value && _step.value != SignInStep.DONE) finishSignIn()
    }

    private fun finishSignIn() {
        _step.value = SignInStep.DONE
        retryId?.let { id -> viewModelScope.launch { retryDownload(id) } }
    }

    /** Leaves the wizard. After a restarted download, show it — that is where the user wants to be. */
    fun onFinished(close: () -> Unit) {
        close()
        if (_step.value == SignInStep.DONE && retryId != null) appNavigator.navigateTo(AppNavTarget.HISTORY)
    }

    companion object {
        const val ARG_RETRY = "retry"
    }
}
