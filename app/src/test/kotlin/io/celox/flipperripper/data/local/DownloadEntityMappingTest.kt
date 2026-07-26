package io.celox.flipperripper.data.local

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.testing.sampleRecord
import org.junit.Test

class DownloadEntityMappingTest {
    @Test
    fun `round-trips a record through the entity`() {
        val record = sampleRecord(id = "z", status = DownloadStatus.COMPLETED)
        val restored = DownloadEntity.fromDomain(record).toDomain()
        assertThat(restored).isEqualTo(record)
    }

    @Test
    fun `unknown enum strings fall back to safe defaults`() {
        val entity =
            DownloadEntity.fromDomain(sampleRecord()).copy(
                platform = "NOPE",
                status = "BOGUS",
                mode = "WEIRD",
            )
        val domain = entity.toDomain()
        assertThat(domain.platform.name).isEqualTo("YOUTUBE")
        assertThat(domain.status).isEqualTo(DownloadStatus.QUEUED)
        assertThat(domain.mode.name).isEqualTo("VIDEO")
    }
}
