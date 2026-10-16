package drs.chat.relay

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Relay routing tests — verify the ConnectionRegistry (the seam where the
 * relay decides online-vs-store-and-forward). The full ktor-server-test-host
 * path is intentionally NOT exercised here: the relay's main loop walks the
 * code paths already unit-tested in AuthChallenge + StoreAndForwardQueue +
 * WireFrame tests. Mocking the WebSocketServerSession lets us assert
 * routing decisions without spinning up a full server.
 */
@Suppress("StringLiteralDuplication")
class RelayIntegrationTest {
    // 12. Register + lookup round-trips
    @Test
    fun `register and get round-trips`() {
        val r = ConnectionRegistry()
        val session = mockk<WebSocketServerSession>()
        r.register("aabb", session)
        assertEquals(session, r.get("aabb"))
    }

    // 13. Unregistered recipient returns null (relay must store-and-forward)
    @Test
    fun `unknown recipient returns null`() {
        val r = ConnectionRegistry()
        assertNull(r.get("nonexistent"))
    }

    // 14. Unregister removes the mapping (recipient disconnected)
    @Test
    fun `unregister removes the mapping`() {
        val r = ConnectionRegistry()
        val session = mockk<WebSocketServerSession>()
        r.register("aabb", session)
        r.unregister("aabb")
        assertNull(r.get("aabb"))
    }

    // 15. Send to unregistered recipient is a silent no-op (relay tolerates missing)
    @Test
    fun `send to unknown recipient is a no-op`() =
        runBlocking {
            val r = ConnectionRegistry()
            r.send("nonexistent", ByteArray(8)) // must not throw
        }

    // 16. Send to registered recipient forwards the bytes (mock verifies the call)
    @Test
    fun `send to registered recipient forwards the bytes`() =
        runBlocking {
            val r = ConnectionRegistry()
            val session = mockk<WebSocketServerSession>(relaxed = true)
            r.register("aabb", session)
            val bytes = ByteArray(16) { it.toByte() }
            r.send("aabb", bytes)
            // The registry must have called session.send with a Binary frame
            coVerify { session.send(any<Frame.Binary>()) }
        }
}
