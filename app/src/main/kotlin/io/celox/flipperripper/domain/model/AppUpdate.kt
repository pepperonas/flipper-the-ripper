package io.celox.flipperripper.domain.model

/** A published app release that may be newer than the installed build. [version] keeps the raw tag. */
data class AppUpdate(val version: String, val url: String)
