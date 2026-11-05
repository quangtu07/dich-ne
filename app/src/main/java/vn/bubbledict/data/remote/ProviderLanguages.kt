package vn.bubbledict.data.remote

/**
 * Spec 1.26/1.28 (F14): **bảng mã ngôn ngữ riêng từng provider** — không dùng thẳng ISO.
 *
 * - MyMemory nhận `zh`, `ja`, `ko`, `en`, `vi` (test thật của Chief).
 * - Mozhi (engine Google) **không nhận `zh` trần** — phải map `zh → zh-CN`; `ja`/`ko` giữ nguyên.
 *   Nếu `zh-CN` bị từ chối (một số instance chỉ có `zh-TW`) thì thử mã thay thế trước khi kết luận
 *   `PAIR_UNSUPPORTED` (spec 1.26: "provider từ chối mã thì thử mã thay thế trước").
 * - Provider có key dùng ISO thường (không map).
 *
 * Một chỗ duy nhất để thêm provider mới không phải rải if trong từng client (CR-04).
 */
object ProviderLanguages {

    private const val HAN = "zh"
    private const val HAN_PROVIDER = "zh-CN"

    /** Bản Đài Loan — dùng khi `zh-CN` bị từ chối (low CR 1.0.5, trả ở 1.0.6). */
    const val HAN_PROVIDER_FALLBACK = "zh-TW"

    /** MyMemory: ISO thường (đã test `zh/ja/ko` chạy). */
    fun myMemory(iso: String): String = iso.lowercase()

    /** Mozhi: `zh` phải thành `zh-CN` (nếu không: "Source language code invalid"). */
    fun mozhi(iso: String): String {
        val code = iso.lowercase()
        return if (code == HAN) HAN_PROVIDER else code
    }

    /** Mã thay thế cho [mozhi] khi provider từ chối mã chính; null = không có phương án khác. */
    fun mozhiAlternate(iso: String): String? =
        if (iso.lowercase() == HAN) HAN_PROVIDER_FALLBACK else null
}