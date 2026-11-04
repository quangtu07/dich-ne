package vn.bubbledict.domain

import vn.bubbledict.domain.model.TextKind

/**
 * Phân loại WORD / SENTENCE / PARAGRAPH theo spec §5.4 + O3 (BE-04). Heuristic, cố ý đơn giản:
 *
 * 1. ≥ 2 dấu kết câu `.` `!` `?`  -> PARAGRAPH (nhiều câu = dịch một lần cho cả đoạn).
 * 2. Có xuống dòng và > 3 token   -> PARAGRAPH (đoạn nhiều dòng không có dấu kết câu).
 * 3. ≤ 3 token và không kết thúc bằng `.` `!` `?` `;` `:` -> WORD.
 * 4. Còn lại -> SENTENCE.
 *
 * Text rỗng coi như WORD; pipeline (BE-10) phải chặn text rỗng trước khi gọi classifier.
 */
object TextClassifier {

    private const val WORD_MAX_TOKENS = 3

    private val sentenceMarks = setOf('.', '!', '?')

    /** `;`/`:` ở cuối cũng loại khỏi WORD (spec §5.4) nhưng không tính là dấu kết câu. */
    private val wordTerminators = setOf('.', '!', '?', ';', ':')

    fun classify(raw: String): TextKind {
        val text = NormalizeText.normalize(raw)
        val marks = text.count { it in sentenceMarks }
        val tokens = NormalizeText.tokenCount(text)

        if (marks >= 2) return TextKind.PARAGRAPH
        if (NormalizeText.hasLineBreak(raw) && tokens > WORD_MAX_TOKENS) return TextKind.PARAGRAPH
        if (tokens <= WORD_MAX_TOKENS && text.lastOrNull() !in wordTerminators) return TextKind.WORD
        return TextKind.SENTENCE
    }
}