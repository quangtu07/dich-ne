package vn.bubbledict.domain

/**
 * Chuẩn hoá text theo spec §5.4 (BE-04):
 * trim, gộp khoảng trắng và xuống dòng, bỏ ký tự bao ngoài không phải chữ (ngoặc kép/đơn, ngoặc đơn,
 * guillemets…). **Giữ** dấu kết câu `.` `!` `?` `;` `:` vì [TextClassifier] cần chúng.
 */
object NormalizeText {

    private const val WRAPPERS = "\"'“”‘’«»()[]{}<>"

    private val whitespace = Regex("\\s+")

    fun normalize(raw: String): String {
        var text = raw.replace(whitespace, " ").trim()
        // Bóc wrapper ở hai đầu, lặp để xử lý `( "hello" )`.
        while (text.isNotEmpty() && text.first() in WRAPPERS) text = text.drop(1).trim()
        while (text.isNotEmpty() && text.last() in WRAPPERS) text = text.dropLast(1).trim()
        return text.replace(whitespace, " ").trim()
    }

    /** Số token (tách theo khoảng trắng sau chuẩn hoá). */
    fun tokenCount(normalized: String): Int =
        if (normalized.isBlank()) 0 else normalized.split(' ').count { it.isNotEmpty() }

    fun hasLineBreak(raw: String): Boolean = raw.contains('\n') || raw.contains('\r')
}