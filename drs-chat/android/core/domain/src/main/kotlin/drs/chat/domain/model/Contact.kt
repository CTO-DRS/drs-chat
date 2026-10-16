package drs.chat.domain.model

import drs.chat.protocol.ContactId

enum class MessageStatus { QUEUED, SENT, DELIVERED, READ, FAILED }

enum class Direction { INCOMING, OUTGOING }

enum class VerificationLevel { UNVERIFIED, VERIFIED }

data class Contact(
    val keyId: ContactId,
    val displayName: String,
    val ed25519Pub: ByteArray,
    val x25519Pub: ByteArray,
    val verified: VerificationLevel = VerificationLevel.UNVERIFIED,
    val addedAt: Long,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is Contact && keyId == other.keyId)

    override fun hashCode(): Int = keyId.hashCode()
}
