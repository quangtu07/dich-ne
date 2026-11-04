package vn.bubbledict.data.remote

/**
 * F14 (BE-34): tên ngôn ngữ cho prompt của provider có key + kiểm tra output.
 * Chỉ 5 ngôn ngữ của 1.0.5; ngoài phạm vi -> mặc định tiếng Việt (giữ hành vi 1.0.4).
 */
object TargetLanguages {

    fun name(iso: String): String = when (iso.lowercase()) {
        "en" -> "English"
        "vi" -> "Vietnamese"
        "zh" -> "Chinese (Simplified)"
        "ko" -> "Korean"
        "ja" -> "Japanese"
        else -> "Vietnamese"
    }

    fun isVietnamese(iso: String): Boolean = iso.equals("vi", ignoreCase = true)

    fun isEnglish(iso: String): Boolean = iso.equals("en", ignoreCase = true)
}
