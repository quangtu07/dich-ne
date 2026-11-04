package vn.bubbledict.domain

/**
 * BE-32 / F14 (spec 1.23): nhận diện **ngôn ngữ nguồn** bằng heuristic Unicode script,
 * không thêm dependency ML Kit language-id (giữ APK nhỏ).
 *
 * Phạm vi 5 ngôn ngữ: EN, VI, ZH, KO, JA — tất cả đều dùng bảng mã Unicode riêng nên đếm
 * ký tự theo script là đủ chính xác cho text đọc sách:
 * - Hangul → KO.
 * - Hiragana/Katakana → JA (kể cả câu trộn Kanji).
 * - Chữ Hán (CJK ideograph) không có kana → ZH (nhưng đánh dấu `uncertain` vì Kanji thuần
 *   cũng có thể là tiếng Nhật — spec R42).
 * - Latin: có ký tự riêng tiếng Việt (`ă â đ ê ô ơ ư` + dấu thanh) → VI, còn lại → EN.
 *
 * Kết quả `uncertain = true` khi: text quá ngắn (< [MIN_CONFIDENT_LETTERS] chữ), trộn nhiều
 * script ngang nhau, hoặc không có chữ nào — pipeline/UI hiện chip `? → VI` (E29), không đoán bừa.
 */
object LanguageDetector {

    const val EN = "en"
    const val VI = "vi"
    const val ZH = "zh"
    const val KO = "ko"
    const val JA = "ja"

    /** Ngôn ngữ đích hỗ trợ ở 1.0.5 (spec 1.23 §5.11). */
    val SUPPORTED = listOf(VI, EN, ZH, KO, JA)

    /** Dưới ngưỡng này thì một ký tự lạ cũng có thể lật kết quả -> đánh dấu không chắc. */
    const val MIN_CONFIDENT_LETTERS = 3

    /** Hán thuần ≥ ngưỡng này thì chắc là tiếng Trung (Hán ngắn có thể là Kanji Nhật — R42). */
    const val HAN_CONFIDENT_LETTERS = 10

    data class Detection(
        /** Mã ISO 639-1 thường; rỗng khi không có chữ nào. */
        val lang: String,
        val uncertain: Boolean,
    ) {
        /** Cặp ngôn ngữ cho chip sheet: "ZH → VI"; lang rỗng -> "? → VI". */
        fun chipLabel(targetLang: String): String =
            "${if (lang.isBlank() || uncertain) "?" else lang.uppercase()} → ${targetLang.uppercase()}"
    }

    private val VIETNAMESE_CHARS =
        ("ăâđêôơư" +
            "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ").toSet()

    /** Đếm script; trả về (lang, số ký tự) của script trội hoặc null nếu text không có chữ. */
    fun detect(text: String): Detection {
        var han = 0
        var kana = 0
        var hangul = 0
        var latin = 0
        var vietnamese = 0

        for (ch in text) {
            when {
                ch.isHangulSyllable() -> hangul++
                ch.isKana() -> kana++
                ch.isHan() -> han++
                ch.isLetter() && ch.code < 0x0250 -> { // Latin cơ bản + Latin mở rộng
                    latin++
                    if (ch.lowercaseChar() in VIETNAMESE_CHARS) vietnamese++
                }
            }
        }

        val letters = han + kana + hangul + latin
        if (letters == 0) return Detection(lang = "", uncertain = true)

        // Tiếng Việt: có dấu riêng là dấu hiệu mạnh nhất (EN không dùng các ký tự này).
        if (vietnamese > 0 && vietnamese * 10 >= latin) {
            return Detection(VI, uncertain = letters < MIN_CONFIDENT_LETTERS)
        }

        val candidates = linkedMapOf(
            KO to hangul,
            JA to kana,
            ZH to han,
            EN to latin,
        ).filterValues { it > 0 }
        if (candidates.isEmpty()) return Detection(lang = "", uncertain = true)

        val sorted = candidates.entries.sortedByDescending { it.value }
        val top = sorted.first()
        val second = sorted.getOrNull(1)

        // Kanji + kana -> JA; Kanji thuần: **đủ dài (≥10 chữ) -> chắc ZH** (spec 1.25/AC39,
        // chỉ Hán ngắn mới có thể là Kanji Nhật -> uncertain). Chip đủ dài phải là "ZH → VI".
        if (top.key == ZH && kana == 0) {
            return Detection(ZH, uncertain = han < HAN_CONFIDENT_LETTERS)
        }
        if (top.key == JA) {
            // Có kana thì chắc là tiếng Nhật, trừ khi Hangul/Hán áp đảo bất thường.
            return Detection(JA, uncertain = letters < MIN_CONFIDENT_LETTERS)
        }
        // Trộn script ngang nhau (top ≤ 2× second) -> không chắc.
        val mixed = second != null && top.value <= second.value * 2
        return Detection(top.key, uncertain = mixed || letters < MIN_CONFIDENT_LETTERS)
    }

    private fun Char.isHan(): Boolean = code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF

    private fun Char.isKana(): Boolean = code in 0x3040..0x309F || code in 0x30A0..0x30FF

    private fun Char.isHangulSyllable(): Boolean = code in 0xAC00..0xD7AF
}