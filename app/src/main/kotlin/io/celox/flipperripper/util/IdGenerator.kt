package io.celox.flipperripper.util

import java.util.UUID
import javax.inject.Inject

/** Abstracts id creation so tests can supply deterministic ids. */
interface IdGenerator {
    fun newId(): String
}

class UuidGenerator
@Inject
constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
