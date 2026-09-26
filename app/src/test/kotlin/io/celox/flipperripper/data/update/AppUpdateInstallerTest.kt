package io.celox.flipperripper.data.update

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.InstallableApk
import io.celox.flipperripper.domain.model.UpdateFailure
import io.celox.flipperripper.domain.model.UpdateInstallState
import io.celox.flipperripper.domain.repository.InstallableApkSource
import io.celox.flipperripper.domain.repository.PackageInstallGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(300_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun apk(version: String = "v1.14.0", sha256: String = sha) =
        InstallableApk(version, "flipper-the-ripper-$version.apk", "https://flipper-the-ripper.celox.io/apk/x", bytes.size.toLong(), sha256)

    private class FakeSource(var release: InstallableApk?, val body: ByteArray) : InstallableApkSource {
        var downloads = 0
        var failWith: IOException? = null
        var writeOnly: Int? = null

        override suspend fun latest() = release

        override suspend fun download(apk: InstallableApk, target: File, onProgress: (Long, Long) -> Unit) {
            downloads++
            val n = writeOnly ?: body.size
            target.writeBytes(body.copyOf(n))
            onProgress(n.toLong(), apk.size)
            failWith?.let { throw it }
        }
    }

    private class FakeGateway(var allowed: Boolean = true) : PackageInstallGateway {
        val installed = mutableListOf<File>()
        var throwOnInstall = false

        override fun canInstallPackages() = allowed

        override fun install(apk: File) {
            check(!throwOnInstall) { "session" }
            installed += apk
        }
    }

    private lateinit var dir: File

    private fun TestScope.installer(source: FakeSource, gateway: FakeGateway): AppUpdateInstaller {
        dir = tmp.newFolder("app-update")
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AppUpdateInstaller(source, gateway, dir, CoroutineScope(dispatcher), dispatcher)
    }

    @Test
    fun `a verified apk goes to the package installer`() =
        runTest {
            val gateway = FakeGateway()
            val installer = installer(FakeSource(apk(), bytes), gateway)
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Installing)
            assertThat(gateway.installed.single().readBytes()).isEqualTo(bytes)
        }

    @Test
    fun `a file that does not hash to the published sha256 is deleted and never installed`() =
        runTest {
            val gateway = FakeGateway()
            val installer = installer(FakeSource(apk(sha256 = "0".repeat(64)), bytes), gateway)
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed(UpdateFailure.CHECKSUM))
            assertThat(gateway.installed).isEmpty()
            assertThat(dir.listFiles()!!.toList()).isEmpty()
        }

    @Test
    fun `without the install permission it waits and continues once it is granted`() =
        runTest {
            val gateway = FakeGateway(allowed = false)
            val installer = installer(FakeSource(apk(), bytes), gateway)
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.NeedsPermission)
            // Coming back without switching it on changes nothing.
            installer.onPermissionMaybeGranted()
            assertThat(gateway.installed).isEmpty()
            gateway.allowed = true
            installer.onPermissionMaybeGranted()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Installing)
            assertThat(gateway.installed).hasSize(1)
        }

    @Test
    fun `a release that is not newer is not installed`() =
        runTest {
            val gateway = FakeGateway()
            val installer = installer(FakeSource(apk(version = "v1.13.0"), bytes), gateway)
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed(UpdateFailure.NO_RELEASE))
            assertThat(gateway.installed).isEmpty()
        }

    @Test
    fun `no release reachable is reported, not ignored`() =
        runTest {
            val installer = installer(FakeSource(null, bytes), FakeGateway())
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed(UpdateFailure.NO_RELEASE))
        }

    @Test
    fun `a broken download keeps its bytes so the next attempt can resume`() =
        runTest {
            val source = FakeSource(apk(), bytes).apply {
                writeOnly = 1000
                failWith = IOException("reset")
            }
            val installer = installer(source, FakeGateway())
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed(UpdateFailure.NETWORK))
            assertThat(File(dir, apk().name).length()).isEqualTo(1000L)
        }

    @Test
    fun `a second tap while it runs does not start a second download`() =
        runTest {
            val source = FakeSource(apk(), bytes)
            val installer = installer(source, FakeGateway())
            installer.start("1.13.0")
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(source.downloads).isEqualTo(1)
        }

    @Test
    fun `apks of other versions are cleared from the cache`() =
        runTest {
            val installer = installer(FakeSource(apk(), bytes), FakeGateway())
            File(dir, "flipper-the-ripper-v1.12.0.apk").writeText("old")
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(dir.list()!!.toList()).containsExactly(apk().name)
        }

    @Test
    fun `the installer's answer ends the flow`() =
        runTest {
            val installer = installer(FakeSource(apk(), bytes), FakeGateway())
            installer.onInstallResult(InstallOutcome.FAILED)
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed(UpdateFailure.INSTALL))
            installer.dismissFailure()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Idle)
            installer.onInstallResult(InstallOutcome.CANCELLED)
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Idle)
        }

    @Test
    fun `an installer that throws is reported as a failed install`() =
        runTest {
            val gateway = FakeGateway().apply { throwOnInstall = true }
            val installer = installer(FakeSource(apk(), bytes), gateway)
            installer.start("1.13.0")
            advanceUntilIdle()
            assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed(UpdateFailure.INSTALL))
        }
}
