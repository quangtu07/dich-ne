package vn.bubbledict.ui.settings

import androidx.compose.runtime.Immutable
import vn.bubbledict.ui.result.Voice

/**
 * Model UI cho tab Cài đặt (UX-03, FE-05).
 *
 * Chưa nối ViewModel: khi BE-06/BE-09/BE-13 xong, `SettingsViewModel` đọc
 * DataStore/SecretStore/BubbleService rồi map sang đây. Composable không tự đọc/ghi dữ liệu.
 */

enum class PermissionStatus { GRANTED, MISSING, BLOCKED }

enum class ApiKeyStatus { MISSING, SAVED }

enum class ApiKeyTestStatus { IDLE, CHECKING, OK, FAILED }

/** Trạng thái dòng "Nguồn miễn phí" (Designer §3.4). */
enum class FreeSourceStatus { ON, QUOTA_EXHAUSTED, OFF }

/** 5 state của item "Tải gói dịch offline" (UX-03 §3.5). */
enum class ModelDownloadStatus { NOT_DOWNLOADED, DOWNLOADING, READY, FAILED }

/** Route trong tab Cài đặt; màn con không phải Activity riêng. */
enum class SettingsRoute {
    LIST,
    API_KEY,
    PROVIDER,
    MODEL_DOWNLOAD,
    PERMISSION_OVERLAY,
    PERMISSION_NOTIFICATION,
    PERMISSION_SCREENSHOT,
}

@Immutable
data class ModelDownloadUi(
    val status: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val downloadedLabel: String = "0",
    val totalLabel: String = "30",
)

@Immutable
data class SettingsUiState(
    val bubbleEnabled: Boolean = false,
    /** F11 privacy: tắt = chế độ chỉ offline (cache + ML Kit). Mặc định BẬT. */
    val onlineSourcesEnabled: Boolean = true,
    val freeSourceStatus: FreeSourceStatus = FreeSourceStatus.ON,
    val overlayPermission: PermissionStatus = PermissionStatus.MISSING,
    val notificationPermission: PermissionStatus = PermissionStatus.GRANTED,
    val apiKeyStatus: ApiKeyStatus = ApiKeyStatus.MISSING,
    val apiKeyLast4: String? = null,
    val apiKeyTest: ApiKeyTestStatus = ApiKeyTestStatus.IDLE,
    /** Provider OpenAI-compatible (Groq mặc định) — tuỳ chọn, dùng khi có key. */
    val openAiKeyStatus: ApiKeyStatus = ApiKeyStatus.MISSING,
    val openAiKeyLast4: String? = null,
    /** AC27: email liên hệ MyMemory; rỗng = 5k ký tự/ngày ẩn danh. */
    val myMemoryContactEmail: String = "",
    val openAiBaseUrl: String = "",
    val openAiModel: String = "",
    val voice: Voice = Voice.US,
    val ukAvailable: Boolean = true,
    val model: ModelDownloadUi = ModelDownloadUi(),
    val cacheSizeLabel: String = "0 KB",
    val versionLabel: String = "0.1.0-dev (1)",
) {
    /** AC23: không xin quyền nào khi mới mở app — chỉ items hiện trạng thái. */
    val needsNotificationSetup: Boolean
        get() = notificationPermission != PermissionStatus.GRANTED
}

@Immutable
data class SettingsActions(
    val onBubbleToggle: (Boolean) -> Unit = {},
    val onOverlayPermissionClick: () -> Unit = {},
    val onNotificationPermissionClick: () -> Unit = {},
    val onNotificationResult: (Boolean) -> Unit = {},
    val onApiKeySave: (String) -> Unit = {},
    val onApiKeyDelete: () -> Unit = {},
    val onApiKeyTest: (String) -> Unit = {},
    val onMyMemoryEmailSave: (String) -> Unit = {},
    val onOpenAiKeySave: (String) -> Unit = {},
    val onOpenAiConfigSave: (String, String) -> Unit = { _, _ -> },
    val onStartModelDownload: () -> Unit = {},
    val onDeleteModel: () -> Unit = {},
    val onVoiceChange: (Voice) -> Unit = {},
    val onDownloadVoice: () -> Unit = {},
    val onOnlineSourcesToggle: (Boolean) -> Unit = {},
    val onDeleteHistory: () -> Unit = {},
    val onDeleteVocab: () -> Unit = {},
    val onDeleteCache: () -> Unit = {},
)