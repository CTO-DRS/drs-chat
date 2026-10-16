package drs.chat.protocol

object EnvelopeConstants {
    const val PROTOCOL_VERSION: Int = 1
    const val MAX_ENVELOPE_BYTES: Int = 64 * 1024
    const val TTL_DEFAULT_SECONDS: Int = 7 * 24 * 60 * 60
    const val CLOCK_SKEW_SECONDS: Long = 300L
    const val MESSAGE_ID_BYTES: Int = 16
    const val KEY_ID_BYTES: Int = 16
    const val SIGNATURE_BYTES: Int = 64
    const val FINGERPRINT_BYTES: Int = 32
    const val WORD_LIST_BYTES: Int = 8
    const val INNER_TEXT_MAX_CHARS: Int = 4096
}

/**
 * Envelope kind byte — FROZEN wire contract per ADR-0009 (owner adjudication).
 * kind 4 = READ_RECEIPT, kind 5 = DELIVERY_RECEIPT (both ratcheted under the
 * live Signal session, never relay-granted). Kinds 2/3 reserved pending owner
 * confirmation — do not guess. The reconstruction's earlier DELIVERED=1/READ=2
 * numbering is SUPERSEDED.
 */
enum class EnvelopeKind(val code: UByte) {
    MESSAGE(0u),
    SESSION_RESET(1u),

    // 2, 3 reserved — do not assign without an ADR.
    READ_RECEIPT(4u),
    DELIVERY_RECEIPT(5u),
    ;

    companion object {
        fun fromCode(code: UByte): EnvelopeKind =
            entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("unknown envelope kind: $code")
    }
}

class UnsupportedEnvelopeVersionException(val found: Int) :
    RuntimeException("unsupported protocolVersion=$found (supported: ${EnvelopeConstants.PROTOCOL_VERSION})")

class EnvelopeTooLargeException(val size: Int) :
    RuntimeException("envelope $size B exceeds ${EnvelopeConstants.MAX_ENVELOPE_BYTES} B cap")
