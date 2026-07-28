package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

/**
 * The routing decides which engine handles which platform, and in what fallback order. It is the whole
 * point of "works reliably without choosing a source by hand", so the order per platform is pinned.
 */
class EngineRoutingTest {
    @Test
    fun `youtube runs on device, never server-first`() {
        // YouTube blocks the server's datacenter IP, so on-device must lead.
        assertThat(EngineRouting.order(Platform.YOUTUBE, serverConfigured = false, preferServer = false))
            .containsExactly(EngineKind.ON_DEVICE)
        assertThat(EngineRouting.order(Platform.YOUTUBE, serverConfigured = true, preferServer = false))
            .containsExactly(EngineKind.ON_DEVICE, EngineKind.SERVER)
            .inOrder()
    }

    @Test
    fun `instagram runs through the webview first`() {
        // The yt-dlp bundle cannot do Instagram at all; the WebView is the only on-device path.
        assertThat(EngineRouting.order(Platform.INSTAGRAM, serverConfigured = false, preferServer = false))
            .containsExactly(EngineKind.WEB_VIEW)
        assertThat(EngineRouting.order(Platform.INSTAGRAM, serverConfigured = true, preferServer = false))
            .containsExactly(EngineKind.WEB_VIEW, EngineKind.SERVER)
            .inOrder()
    }

    @Test
    fun `tiktok runs through the webview first`() {
        assertThat(EngineRouting.order(Platform.TIKTOK, serverConfigured = true, preferServer = false))
            .containsExactly(EngineKind.WEB_VIEW, EngineKind.SERVER)
            .inOrder()
    }

    @Test
    fun `an explicit server preference puts the server first, primary as fallback`() {
        assertThat(EngineRouting.order(Platform.YOUTUBE, serverConfigured = true, preferServer = true))
            .containsExactly(EngineKind.SERVER, EngineKind.ON_DEVICE)
            .inOrder()
        assertThat(EngineRouting.order(Platform.INSTAGRAM, serverConfigured = true, preferServer = true))
            .containsExactly(EngineKind.SERVER, EngineKind.WEB_VIEW)
            .inOrder()
    }

    @Test
    fun `without a server there is exactly one engine to try`() {
        Platform.entries.forEach { platform ->
            val order = EngineRouting.order(platform, serverConfigured = false, preferServer = false)
            assertThat(order).hasSize(1)
            assertThat(order).doesNotContain(EngineKind.SERVER)
        }
    }
}
