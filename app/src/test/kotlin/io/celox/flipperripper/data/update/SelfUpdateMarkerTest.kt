package io.celox.flipperripper.data.update

import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SelfUpdateMarkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the marker is read once, so only the self-update gets the reopen notification`() {
        assertThat(SelfUpdateMarker.consume(context)).isFalse()
        SelfUpdateMarker.set(context)
        assertThat(SelfUpdateMarker.consume(context)).isTrue()
        // A later manual install (Android shows its own "Open") must not repeat it.
        assertThat(SelfUpdateMarker.consume(context)).isFalse()
    }

    @Test
    fun `the installer's statuses map to what the app acts on`() {
        assertThat(InstallResultReceiver.outcomeOf(PackageInstaller.STATUS_SUCCESS)).isEqualTo(InstallOutcome.SUCCESS)
        // The user tapped Cancel on Android's screen: not an error to show.
        assertThat(InstallResultReceiver.outcomeOf(PackageInstaller.STATUS_FAILURE_ABORTED))
            .isEqualTo(InstallOutcome.CANCELLED)
        listOf(
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INVALID,
        ).forEach { assertThat(InstallResultReceiver.outcomeOf(it)).isEqualTo(InstallOutcome.FAILED) }
    }
}
