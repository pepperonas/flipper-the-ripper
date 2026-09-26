package io.celox.flipperripper.data.update

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.InstallableApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** The resumable download against a real local HTTP server — the part a fake cannot prove. */
class ApkReleaseSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val body = ByteArray(700_000) { (it * 7 % 256).toByte() }
    private val ranges = mutableListOf<String?>()
    private var honourRange = true

    /** A tiny HTTP/1.1 server on a real socket: one request per connection, then close. */
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val loop =
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { serve(it) }
            }
        }

    private fun serve(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
        val range = headers.firstOrNull { it.startsWith("Range:", ignoreCase = true) }?.substringAfter(":")?.trim()
        synchronized(ranges) { ranges += range }
        val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt()
        val out = socket.getOutputStream()
        if (from != null && honourRange) {
            val n = body.size - from
            out.write(
                (
                    "HTTP/1.1 206 Partial Content\r\nContent-Length: $n\r\n" +
                        "Content-Range: bytes $from-${body.size - 1}/${body.size}\r\nConnection: close\r\n\r\n"
                    ).toByteArray(),
            )
            out.write(body, from, n)
        } else {
            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(body)
        }
        out.flush()
    }

    @After fun stop() {
        server.close()
        loop.join(1000)
    }

    private val apk get() =
        InstallableApk("v9.0.0", "a.apk", "http://127.0.0.1:${server.localPort}/apk", body.size.toLong(), "x")

    private val source = ApkReleaseSource(Dispatchers.IO)

    @Test
    fun `a partial file is continued with a range request`() =
        runBlocking {
            val target = tmp.newFile("a.apk").apply { writeBytes(body.copyOf(250_000)) }
            source.download(apk, target) { _, _ -> }
            assertThat(ranges).containsExactly("bytes=250000-")
            assertThat(target.readBytes()).isEqualTo(body)
        }

    @Test
    fun `a server that ignores the range sends everything, and the file is rewritten, not appended`() =
        runBlocking {
            honourRange = false
            val target = tmp.newFile("a.apk").apply { writeBytes(body.copyOf(250_000)) }
            source.download(apk, target) { _, _ -> }
            assertThat(target.readBytes()).isEqualTo(body)
        }

    @Test
    fun `a complete file is not fetched again`() =
        runBlocking {
            val target = tmp.newFile("a.apk").apply { writeBytes(body) }
            source.download(apk, target) { _, _ -> }
            assertThat(ranges).isEmpty()
        }

    @Test
    fun `progress ends at the full size`() =
        runBlocking {
            val target = tmp.newFile("a.apk").apply { delete() }
            var last = 0L
            source.download(apk, target) { have, _ -> last = have }
            assertThat(last).isEqualTo(body.size.toLong())
        }
}
