package io.celox.flipperripper.domain.usecase

import io.celox.flipperripper.domain.repository.ClipboardRepository
import io.celox.flipperripper.domain.util.ParsedUrl
import javax.inject.Inject

/** Return a supported URL currently on the clipboard, if any. */
class PeekClipboardUrlUseCase
@Inject
constructor(private val repository: ClipboardRepository) {
    operator fun invoke(): ParsedUrl? = repository.peekSupportedUrl()
}
