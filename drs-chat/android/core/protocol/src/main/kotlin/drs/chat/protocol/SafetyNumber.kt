package drs.chat.protocol

import java.security.MessageDigest

object SafetyNumber {
    private const val DOMAIN = "drs-safety-v1"
    private const val GROUPS = 12

    fun compute(
        meEd25519Pub: ByteArray,
        themEd25519Pub: ByteArray,
    ): String {
        require(meEd25519Pub.size == 32 && themEd25519Pub.size == 32) { "Ed25519 pub must be 32 bytes" }
        val (a, b) = if (meEd25519Pub.lex(themEd25519Pub) <= 0) meEd25519Pub to themEd25519Pub else themEd25519Pub to meEd25519Pub
        val md = MessageDigest.getInstance("SHA-256")
        md.update(0x02.toByte())
        md.update(DOMAIN.toByteArray(Charsets.UTF_8))
        md.update(a)
        md.update(b)
        val d = md.digest()
        val sb = StringBuilder(GROUPS * 5)
        for (i in 0 until GROUPS) {
            val v = ((d[2 * i].toInt() and 0xFF) shl 8) or (d[2 * i + 1].toInt() and 0xFF)
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append((v % 100000).toString().padStart(5, '0'))
        }
        return sb.toString()
    }

    fun groups(
        me: ByteArray,
        them: ByteArray,
    ): List<String> = compute(me, them).split(' ')

    private fun ByteArray.lex(o: ByteArray): Int {
        val n = minOf(size, o.size)
        for (i in 0 until n) {
            val c = (this[i].toInt() and 0xFF) - (o[i].toInt() and 0xFF)
            if (c != 0) return c
        }
        return size - o.size
    }
}
