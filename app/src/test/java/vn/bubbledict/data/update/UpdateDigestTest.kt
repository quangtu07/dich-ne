package vn.bubbledict.data.update

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F12 — SHA-256 của APK tải về: phải trùng manifest mới cho cài.
 * Có test vector chuẩn để chắc code băm đúng thuật toán.
 */
class UpdateDigestTest {

    @Test
    fun knownVector_matchesStandardSha256() {
        // "abc" -> ba7816bf… (test vector chuẩn của SHA-256)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateDigest.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun streaming_digestEqualsInMemoryDigest() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val inMemory = UpdateDigest.sha256Hex(data)
        val streamed = ByteArrayInputStream(data).use { input ->
            UpdateDigest.sha256Hex(read = { buffer -> input.read(buffer) })
        }
        assertEquals(inMemory, streamed)
        assertEquals(64, streamed.length)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }, streamed)
    }

    @Test
    fun matches_isCaseInsensitiveAndExact() {
        val hex = "3e2761fc15a8d6daf6ffb65a2affe5a14db3b0e7bb7e169f459e0fbf7f532e16"
        assertTrue(UpdateDigest.matches(hex, hex))
        assertTrue(UpdateDigest.matches(hex.uppercase(), hex))
        assertTrue(UpdateDigest.matches(" $hex\n", hex))
        assertFalse(UpdateDigest.matches(hex, hex.dropLast(1) + "f"))
        assertFalse(UpdateDigest.matches("", hex))
        assertFalse(UpdateDigest.matches(hex, ""))
    }

    @Test
    fun differentContent_differentDigest() {
        assertFalse(
            UpdateDigest.matches(
                UpdateDigest.sha256Hex("apk-v1".toByteArray()),
                UpdateDigest.sha256Hex("apk-v2".toByteArray()),
            ),
        )
    }
}