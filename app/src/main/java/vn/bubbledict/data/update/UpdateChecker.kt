package vn.bubbledict.data.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Mã lỗi F12 trả cho UI (banner) — không log URL/text. */
object UpdateErrorCodes {
    /** Không gọi được manifest (mất mạng, 404, timeout) — E24: im lặng. */
    const val NETWORK = "U1"

    /** Manifest sai định dạng / thiếu sha256 hợp lệ. */
    const val BAD_MANIFEST = "U2"

    /** Tải APK thất bại. */
    const val DOWNLOAD = "U3"

    /** SHA-256 không khớp — không cho cài, xoá file (E26). */
    const val CHECKSUM = "U4"

    /** Chưa được cấp quyền "cài từ nguồn không xác định". */
    const val INSTALL_PERMISSION = "U5"
}

/**
 * F12 — đọc `update.json` và so versionCode với bản đang cài.
 *
 * Chỉ dùng HTTP GET + parse JSON; không gửi dữ liệu người dùng, không log URL (nguyên tắc log).
 */
class UpdateChecker(
    private val client: OkHttpClient,
    /** versionCode của bản đang cài, dùng để so với manifest. */
    val currentVersionCode: Int,
) {

    sealed interface Result {
        data class Available(val manifest: UpdateManifest) : Result
        data object UpToDate : Result
        data class Failed(val code: String) : Result
    }

    /** @param manifestUrl URL tuyệt đối tới `update.json` (lấy từ Settings hoặc BuildConfig). */
    suspend fun check(manifestUrl: String): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.Failed(UpdateErrorCodes.NETWORK)
                val body = response.body?.string()
                    ?: return@withContext Result.Failed(UpdateErrorCodes.NETWORK)
                val manifest = UpdateManifestParser.parse(body)
                    ?: return@withContext Result.Failed(UpdateErrorCodes.BAD_MANIFEST)
                when {
                    !manifest.isNewerThan(currentVersionCode) -> Result.UpToDate
                    !manifest.hasValidSha256 -> Result.Failed(UpdateErrorCodes.BAD_MANIFEST)
                    else -> Result.Available(manifest)
                }
            }
        } catch (e: IOException) {
            Result.Failed(UpdateErrorCodes.NETWORK)
        } catch (e: Exception) {
            Result.Failed(UpdateErrorCodes.NETWORK)
        }
    }
}