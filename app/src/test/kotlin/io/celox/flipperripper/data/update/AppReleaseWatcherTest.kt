package io.celox.flipperripper.data.update

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.AppUpdate
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.AppReleaseSource
import io.celox.flipperripper.domain.repository.UpdateNotifications
import io.celox.flipperripper.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppReleaseWatcherTest {
    private var latest: AppUpdate? = AppUpdate("v1.13.0", "https://github.com/pepperonas/flipper-the-ripper/releases/tag/v1.13.0")
    private val shown = mutableListOf<AppUpdate>()
    private var canShow = true
    private val settings = FakeSettingsRepository()

    private val watcher =
        AppReleaseWatcher(
            source = object : AppReleaseSource {
                override suspend fun fetchLatestRelease() = latest
            },
            settings = settings,
            notifications =
            object : UpdateNotifications {
                override fun show(update: AppUpdate): Boolean {
                    if (canShow) shown += update
                    return canShow
                }
            },
        )

    @Test
    fun `a newer release is notified and remembered for the Home notice`() =
        runTest {
            watcher.check("1.12.0")
            assertThat(shown.map { it.version }).containsExactly("v1.13.0")
            assertThat(settings.knownUpdate.value?.version).isEqualTo("v1.13.0")
            assertThat(settings.notifiedUpdateVersion.first()).isEqualTo("v1.13.0")
        }

    @Test
    fun `the same release is notified only once, however often it is checked`() =
        runTest {
            repeat(3) { watcher.check("1.12.0") }
            assertThat(shown).hasSize(1)
        }

    @Test
    fun `the next release is notified again`() =
        runTest {
            watcher.check("1.12.0")
            latest = AppUpdate("v1.14.0", "https://github.com/x/releases/tag/v1.14.0")
            watcher.check("1.12.0")
            assertThat(shown.map { it.version }).containsExactly("v1.13.0", "v1.14.0").inOrder()
        }

    @Test
    fun `nothing is notified when the installed build is current`() =
        runTest {
            watcher.check("1.13.0")
            assertThat(shown).isEmpty()
        }

    @Test
    fun `switched off, nothing is notified, but the Home notice still learns about it`() =
        runTest {
            settings.state.value = UserPreferences(updateNotifications = false)
            watcher.check("1.12.0")
            assertThat(shown).isEmpty()
            assertThat(settings.knownUpdate.value?.version).isEqualTo("v1.13.0")
        }

    @Test
    fun `a notification that could not be shown is tried again later`() =
        runTest {
            canShow = false // e.g. the notification permission is not granted yet
            watcher.check("1.12.0")
            assertThat(settings.notifiedUpdateVersion.first()).isNull()
            canShow = true
            watcher.check("1.12.0")
            assertThat(shown.map { it.version }).containsExactly("v1.13.0")
        }

    @Test
    fun `no answer from the network changes nothing`() =
        runTest {
            latest = null
            assertThat(watcher.check("1.12.0")).isNull()
            assertThat(shown).isEmpty()
            assertThat(settings.knownUpdate.value).isNull()
        }
}
