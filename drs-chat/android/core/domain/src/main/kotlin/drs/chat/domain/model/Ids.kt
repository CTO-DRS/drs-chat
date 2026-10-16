package drs.chat.domain.model

/**
 * Domain-only identifiers. KeyId/ContactId now live in :core:protocol
 * (shared with :core:crypto) — see Amendment to ADR-0002 (M3 push). The
 * types below are referenced only inside :core:domain.
 */
@JvmInline value class MessageId(val bytes: ByteArray) {
    init {
        require(bytes.size == MESSAGE_ID_BYTES) { "MessageId must be $MESSAGE_ID_BYTES bytes" }
    }

    companion object {
        const val MESSAGE_ID_BYTES: Int = 16

        fun random(): MessageId = MessageId(ByteArray(MESSAGE_ID_BYTES).also { java.security.SecureRandom().nextBytes(it) })
    }
}

@JvmInline value class TransportId(val value: String)
