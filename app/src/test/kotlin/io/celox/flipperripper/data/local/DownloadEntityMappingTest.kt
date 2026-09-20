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
    fun `the measured progress survives the mapping`() {
        // It did not. The column was written on every progress step and `toDomain` left it behind,
        // so the History card could only ever draw an indeterminate bar while the notification —
        // which reads the engine directly — counted to 100 %.
        val record = sampleRecord(id = "p", status = DownloadStatus.RUNNING, progressPercent = 42f)
        assertThat(DownloadEntity.fromDomain(record).toDomain().progressPercent).isEqualTo(42f)
    }

    @Test
    fun `a row that has never reported progress maps to no progress, not to zero`() {
        // Zero is a measurement; "not measured yet" is not. The UI picks its indeterminate bar off
        // exactly this difference.
        val entity = DownloadEntity.fromDomain(sampleRecord()).copy(progressPercent = null)
        assertThat(entity.toDomain().progressPercent).isNull()
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
