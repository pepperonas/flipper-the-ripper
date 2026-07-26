package io.celox.flipperripper.domain.usecase

import io.celox.flipperripper.domain.util.ParsedUrl
import io.celox.flipperripper.domain.util.UrlParser
import javax.inject.Inject

/** Extract the first supported URL from arbitrary text (share intent extra or pasted string). */
class ResolveUrlUseCase
@Inject
constructor() {
    operator fun invoke(text: String?): ParsedUrl? = UrlParser.extractSupported(text)
}
