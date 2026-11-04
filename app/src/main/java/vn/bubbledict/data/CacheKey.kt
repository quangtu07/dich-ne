package vn.bubbledict.data

import vn.bubbledict.domain.NormalizeText
import vn.bubbledict.domain.model.TextKind
import java.security.MessageDigest

/**
 * Key cache theo plan §4: `sha256(normText.lowercase() + "|" + targetLang + "|" + kind)`.
 *
 * - Chuẩn hoá dùng chung [NormalizeText.normalize] (một định nghĩa duy nhất với classifier)
 *   rồi lowercase, để cùng text gõ khác hoa/thường hoặc thừa khoảng trắng luôn ra cùng key.
 * - `kind` nằm trong key vì WORD/SENTENCE/PARAGRAPH dùng payload khác nhau (O3).
 * - `source` (SELECTION/OCR) **không** vào key: cùng text tra từ menu hay OCR dùng chung cache.
 */
object CacheKey {

    fun normalize(text: String): String = NormalizeText.normalize(text).lowercase()

    fun of(normText: String, targetLang: String, kind: TextKind): String =
        sha256("${normalize(normText)}|${targetLang.trim().lowercase()}|${kind.name}")

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}