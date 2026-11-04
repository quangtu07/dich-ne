package vn.bubbledict.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.bubbledict.domain.model.TextKind

/**
 * BE-07 DoD (phần JVM): cùng text khác hoa/thường + khoảng trắng thừa -> cùng key;
 * khác `kind` -> khác key; `source` không có trong key.
 */
class CacheKeyTest {

    @Test
    fun sameTextDifferingCaseAndSpaces_sameKey() {
        val a = CacheKey.of("  Ubiquitous  ", "vi", TextKind.WORD)
        val b = CacheKey.of("ubiquitous", "vi", TextKind.WORD)
        val c = CacheKey.of("UBIQUITOUS", "vi", TextKind.WORD)
        assertEquals(a, b)
        assertEquals(b, c)

        // nhiều khoảng trắng / xuống dòng giữa từ
        assertEquals(
            CacheKey.of("the   city\twas\nquiet", "vi", TextKind.SENTENCE),
            CacheKey.of("the city was quiet", "vi", TextKind.SENTENCE),
        )
    }

    @Test
    fun differentKind_differentKey() {
        assertNotEquals(
            CacheKey.of("ubiquitous", "vi", TextKind.WORD),
            CacheKey.of("ubiquitous", "vi", TextKind.SENTENCE),
        )
        assertNotEquals(
            CacheKey.of("ubiquitous", "vi", TextKind.SENTENCE),
            CacheKey.of("ubiquitous", "vi", TextKind.PARAGRAPH),
        )
    }

    @Test
    fun differentTargetLang_differentKey() {
        assertNotEquals(
            CacheKey.of("ubiquitous", "vi", TextKind.WORD),
            CacheKey.of("ubiquitous", "en", TextKind.WORD),
        )
    }

    @Test
    fun keyIsSha256Hex() {
        val key = CacheKey.of("ubiquitous", "vi", TextKind.WORD)
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        // known-answer: sha256("ubiquitous|vi|WORD")
        assertEquals(CacheKey.sha256("ubiquitous|vi|WORD"), key)
    }

    @Test
    fun normalize_collapsesWhitespaceAndCase() {
        assertEquals("hello world", CacheKey.normalize("  Hello \n World  "))
        assertEquals("", CacheKey.normalize("   "))
    }
}