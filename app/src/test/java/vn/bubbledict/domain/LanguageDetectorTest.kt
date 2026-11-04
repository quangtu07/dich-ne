package vn.bubbledict.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BE-32 / F14: heuristic nhận diện ngôn ngữ nguồn (EN/VI/ZH/KO/JA) — không dependency.
 * AC39 chấm cơ chế, không chấm chất lượng dịch.
 */
class LanguageDetectorTest {

    @Test
    fun englishParagraph_isEn() {
        val d = LanguageDetector.detect("The city was quiet. Rain fell all night.")
        assertEquals(LanguageDetector.EN, d.lang)
        assertFalse(d.uncertain)
    }

    @Test
    fun vietnameseWithDiatrics_isVi() {
        val d = LanguageDetector.detect("Hôm qua thành phố đã mở một thư viện mới gần sông.")
        assertEquals(LanguageDetector.VI, d.lang)
        assertFalse(d.uncertain)
    }

    @Test
    fun chineseSentence_isZh() {
        val d = LanguageDetector.detect("昨天城市在河边开了一座新图书馆。")
        assertEquals(LanguageDetector.ZH, d.lang)
        // Spec 1.28: Hán thuần >=10 chữ -> chắc là tiếng Trung (chip "ZH → VI"), R42.
        assertFalse("Hán đủ dài phải chắc ZH", d.uncertain)
    }

    @Test
    fun shortHanOnly_isUncertain() {
        // Hán ngắn có thể là Kanji Nhật -> uncertain (chip "? → VI").
        val d = LanguageDetector.detect("好")
        assertEquals(LanguageDetector.ZH, d.lang)
        assertTrue("Hán ngắn phải uncertain", d.uncertain)
    }

    @Test
    fun japaneseWithKana_isJa() {
        val d = LanguageDetector.detect("昨日、街の川のそばに新しい図書館ができました。")
        assertEquals(LanguageDetector.JA, d.lang)
        assertFalse(d.uncertain)
    }

    @Test
    fun korean_isKo() {
        val d = LanguageDetector.detect("어제 도시는 강 근처에 새 도서관을 열었습니다.")
        assertEquals(LanguageDetector.KO, d.lang)
        assertFalse(d.uncertain)
    }

    @Test
    fun veryShortText_isUncertain() {
        val d = LanguageDetector.detect("Hi")
        assertEquals(LanguageDetector.EN, d.lang)
        assertTrue("text quá ngắn phải uncertain", d.uncertain)
    }

    @Test
    fun noLetters_isUncertainWithEmptyLang() {
        val d = LanguageDetector.detect("1234 — !!!")
        assertEquals("", d.lang)
        assertTrue(d.uncertain)
        assertEquals("? → VI", d.chipLabel("vi"))
    }

    @Test
    fun mixedScripts_isUncertain() {
        // Latin 5 chữ vs Hán 4 chữ -> không script nào áp đảo (top ≤ 2×second) -> uncertain.
        val d = LanguageDetector.detect("Hello 你好世界")
        assertTrue("trộn script ngang nhau phải uncertain", d.uncertain)
    }

    @Test
    fun chipLabel_showsPair() {
        assertEquals("ZH → VI", LanguageDetector.Detection("zh", uncertain = false).chipLabel("vi"))
        assertEquals("JA → VI", LanguageDetector.Detection("ja", uncertain = false).chipLabel("VI"))
    }

    @Test
    fun supportedTargets_containsFiveLanguages() {
        assertEquals(
            listOf("vi", "en", "zh", "ko", "ja"),
            LanguageDetector.SUPPORTED,
        )
    }
}