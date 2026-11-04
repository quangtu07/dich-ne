package vn.bubbledict.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Nơi duy nhất đọc/ghi Gemini API key (plan §4, CR-01).
 *
 * - Lưu trong `EncryptedSharedPreferences` (AES256-SIV cho key name, AES256-GCM cho value).
 * - Không log, không đưa key vào history/cache/APK.
 * - File prefs có thể xoá bằng [clear]; sau khi xoá, key coi như chưa cấu hình.
 */
interface SecretStore {
    fun geminiApiKey(): String?

    /** Trả true nếu đã lưu; key rỗng/bị từ chối thì trả false và giữ nguyên key cũ. */
    fun saveGeminiApiKey(key: String): Boolean

    /** Key cho provider OpenAI-compatible (Groq/OpenRouter/Ollama) — tuỳ chọn. */
    fun openAiApiKey(): String?

    fun saveOpenAiApiKey(key: String): Boolean

    fun clear()
}

class EncryptedSecretStore(
    context: Context,
    prefsName: String = PREFS_NAME,
) : SecretStore {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext, prefsName)

    override fun geminiApiKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    override fun saveGeminiApiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        // commit() đồng bộ: trả kết quả thật cho UI, không báo "đã lưu" khi ghi hỏng.
        return prefs.edit().putString(KEY_GEMINI, trimmed).commit()
    }

    override fun openAiApiKey(): String? = prefs.getString(KEY_OPENAI, null)?.takeIf { it.isNotBlank() }

    override fun saveOpenAiApiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        return prefs.edit().putString(KEY_OPENAI, trimmed).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_GEMINI).remove(KEY_OPENAI).commit()
    }

    companion object {
        const val PREFS_NAME = "bubble_secrets"
        const val KEY_GEMINI = "geminiApiKey"
        const val KEY_OPENAI = "openAiApiKey"

        /** Tạo store mã hoá; ném lỗi ra ngoài để UI hiện lỗi thay vì âm thầm lưu plaintext. */
        private fun createPrefs(context: Context, name: String): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}