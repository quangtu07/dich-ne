package vn.bubbledict.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * F12 — manifest cập nhật (`update.json`) host tĩnh, không cần server.
 *
 * Ví dụ:
 * ```json
 * { "versionCode": 2, "versionName": "1.0.1",
 *   "apkUrl": "http://192.168.100.106:8770/nghia-bong-1.0.1.apk",
 *   "sha256": "3e2761fc…", "notes": "Sửa lỗi bong bóng" }
 * ```
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String = "",
    val apkUrl: String,
    /** SHA-256 hex (64 ký tự) của file APK — bắt buộc để chống file lạ. */
    val sha256: String,
    val notes: String = "",
    /** Spec 1.12: ngày phát hành (dạng ISO-8601), chỉ để hiển thị; không dùng để quyết định update. */
    val publishedAt: String = "",
) {
    val hasValidSha256: Boolean get() = sha256.matches(Regex("[0-9a-fA-F]{64}"))

    /** Có bản mới khi versionCode của manifest lớn hơn bản đang cài. */
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}

object UpdateManifestParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** @return null nếu JSON hỏng/thiếu trường; không throw để UI chỉ cần bỏ banner. */
    fun parse(raw: String): UpdateManifest? = runCatching {
        json.decodeFromString<UpdateManifest>(raw)
    }.getOrNull()
}