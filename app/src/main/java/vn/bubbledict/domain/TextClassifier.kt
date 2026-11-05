package vn.bubbledict.domain

import vn.bubbledict.domain.model.TextKind

/**
 * Phân loại WORD / SENTENCE / PARAGRAPH theo spec §5.4 + O3 (BE-04). Heuristic, cố ý đơn giản:
 *
 * **Latin (EN/VI)** — luật 1.0.4:
 * 1. ≥ 2 dấu kết câu `.` `!` `?`  -> PARAGRAPH (nhiều câu = dịch một lần cho cả đoạn).
 * 2. Có xuống dòng và > 3 token   -> PARAGRAPH (đoạn nhiều dòng không có dấu kết câu).
 * 3. ≤ 3 token và không kết thúc bằng `.` `!` `?` `;` `:` -> WORD.
 * 4. Còn lại -> SENTENCE.
 *
 * **CJK/Hangul (ZH/JA/KO)** — spec 1.25/1.26 (BUG-22), đếm **ký tự chữ/số** thay vì token vì các
 * ngôn ngữ này không có dấu cách: ≤3 ký tự -> WORD; ≥60 ký tự hoặc ≥2 dấu kết câu CJK
 * (`。！？；`) -> PARAGRAPH; còn lại -> SENTENCE. Trộn script (vd `iPhone 手机很好用`) cũng áp luật
 * ký tự cho toàn chuỗi, và **chuỗi trộn script không bao giờ là WORD** (chip `Từ` hứa "một từ",
 * chuỗi lai không phải một từ — Designer §9c).
 *
 * Ghi chú 1.0.6: bản 1.0.5 đếm cả khoảng trắng vào "ký tự" để khớp ví dụ spec `OK 好` = 4; nay
 * chỉ đếm chữ/số, còn `OK 好` vẫn SENTENCE nhờ luật trộn script ở trên.
 *
 * Text rỗng coi như WORD; pipeline (BE-10) phải chặn text rỗng trước khi gọi classifier.
 */
object TextClassifier {

    private const val WORD_MAX_TOKENS = 3
    private const val WORD_MAX_CJK_CHARS = 3
    private const val PARAGRAPH_MIN_CJK_CHARS = 60

    private val sentenceMarks = setOf('.', '!', '?')
    /** Dấu kết câu CJK — cũng tính là "nhiều câu". */
    private val cjkSentenceMarks = setOf('。', '！', '？', '；')

    /** `;`/`:` ở cuối cũng loại khỏi WORD (spec §5.4) nhưng không tính là dấu kết câu. */
    private val wordTerminators = setOf('.', '!', '?', ';', ':', '。', '！', '？', '；')

    fun classify(raw: String): TextKind {
        val text = NormalizeText.normalize(raw)
        val cjkChars = text.count { it.isCjkOrHangul() }

        // CJK/Hangul: đếm ký tự (không có dấu cách để tách token).
        if (cjkChars > 0) {
            // 1.0.6: chỉ đếm chữ/số — khoảng trắng không phải "ký tự" (CR low: trước đây cộng cả
            // space làm đoạn CJK+Latin chạm ngưỡng paragraph sớm vài ký tự).
            val letterCount = text.count { it.isLetterOrDigit() }
            val marks = text.count { it in cjkSentenceMarks }
            if (marks >= 2 || letterCount >= PARAGRAPH_MIN_CJK_CHARS) return TextKind.PARAGRAPH
            // Chuỗi trộn script (Latin + CJK/Hangul) luôn ít nhất là SENTENCE: "OK 好" = 3 chữ
            // nhưng không phải một từ, gắn chip "Từ" là nói sai (Designer §9c).
            val mixedScript = text.any { it.isLetter() && !it.isCjkOrHangul() }
            if (!mixedScript && letterCount <= WORD_MAX_CJK_CHARS && text.lastOrNull() !in wordTerminators) {
                return TextKind.WORD
            }
            return TextKind.SENTENCE
        }

        val marks = text.count { it in sentenceMarks }
        val tokens = NormalizeText.tokenCount(text)

        if (marks >= 2) return TextKind.PARAGRAPH
        if (NormalizeText.hasLineBreak(raw) && tokens > WORD_MAX_TOKENS) return TextKind.PARAGRAPH
        if (tokens <= WORD_MAX_TOKENS && text.lastOrNull() !in wordTerminators) return TextKind.WORD
        return TextKind.SENTENCE
    }

    /** Chữ Hán, Kana, Hangul — dùng chung định nghĩa code point với [LanguageDetector]. */
    private fun Char.isCjkOrHangul(): Boolean =
        code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF ||
            code in 0x3040..0x309F || code in 0x30A0..0x30FF ||
            code in 0xAC00..0xD7AF
}