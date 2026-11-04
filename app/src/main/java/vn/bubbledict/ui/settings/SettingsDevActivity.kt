package vn.bubbledict.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import vn.bubbledict.R
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * Màn dev: render [SettingsScreen] với state giả để QA chụp ảnh từng màn/state
 * (không phải UI sản phẩm). Mở bằng adb:
 *
 *   adb shell am start -n vn.bubbledict/.ui.settings.SettingsDevActivity
 *   ... --es route api_key | model | permission
 *   ... --es model downloading|ready|failed|paused
 *   ... --es key saved|missing --es test ok|failed|checking
 *   ... --es permission overlay|notification|screenshot
 */
class SettingsDevActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val route = routeFrom(intent.getStringExtra("route"), intent.getStringExtra("permission"))
        val initialState = SettingsUiState(
            overlayPermission = when (intent.getStringExtra("overlay")) {
                "granted" -> PermissionStatus.GRANTED
                "blocked" -> PermissionStatus.BLOCKED
                else -> PermissionStatus.MISSING
            },
            notificationPermission = when (intent.getStringExtra("notification")) {
                "granted" -> PermissionStatus.GRANTED
                else -> PermissionStatus.MISSING
            },
            apiKeyStatus = if (intent.getStringExtra("key") == "saved") {
                ApiKeyStatus.SAVED
            } else {
                ApiKeyStatus.MISSING
            },
            apiKeyLast4 = if (intent.getStringExtra("key") == "saved") "1a2b" else null,
            apiKeyTest = when (intent.getStringExtra("test")) {
                "ok" -> ApiKeyTestStatus.OK
                "failed" -> ApiKeyTestStatus.FAILED
                "checking" -> ApiKeyTestStatus.CHECKING
                else -> ApiKeyTestStatus.IDLE
            },
            ukAvailable = intent.getStringExtra("uk_missing") != "true",
            model = modelFrom(intent.getStringExtra("model")),
            versionLabel = "0.1.0-dev (1)",
        )

        setContent {
            BubbleDictTheme {
                var state by remember { mutableStateOf(initialState) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        SettingsScreen(
                            state = state,
                            initialRoute = route,
                            onShowMessage = { message ->
                                scope.launch { snackbar.showSnackbar(message) }
                            },
                            actions = SettingsActions(
                                onBubbleToggle = { enabled ->
                                    state = state.copy(bubbleEnabled = enabled)
                                },
                                onApiKeySave = { key ->
                                    state = state.copy(
                                        apiKeyStatus = ApiKeyStatus.SAVED,
                                        apiKeyLast4 = key.takeLast(4),
                                        apiKeyTest = ApiKeyTestStatus.IDLE,
                                    )
                                },
                                onApiKeyDelete = {
                                    state = state.copy(
                                        apiKeyStatus = ApiKeyStatus.MISSING,
                                        apiKeyLast4 = null,
                                        apiKeyTest = ApiKeyTestStatus.IDLE,
                                    )
                                },
                                onApiKeyTest = {
                                    state = state.copy(apiKeyTest = ApiKeyTestStatus.CHECKING)
                                },
                                onStartModelDownload = {
                                    state = state.copy(
                                        model = ModelDownloadUi(
                                            status = ModelDownloadStatus.DOWNLOADING,
                                            progressPercent = 42,
                                            downloadedLabel = "12,6",
                                            totalLabel = "30",
                                        ),
                                    )
                                },
                                onDeleteModel = { state = state.copy(model = ModelDownloadUi()) },
                                onVoiceChange = { voice -> state = state.copy(voice = voice) },
                                onDownloadVoice = {
                                    scope.launch { snackbar.showSnackbar("Mở cài đặt giọng (dev)") }
                                },
                                onDeleteHistory = {},
                                onDeleteVocab = {},
                                onDeleteCache = {},
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun routeFrom(route: String?, permission: String?): SettingsRoute = when (route) {
        "api_key" -> SettingsRoute.API_KEY
        "model" -> SettingsRoute.MODEL_DOWNLOAD
        "permission" -> when (permission) {
            "notification" -> SettingsRoute.PERMISSION_NOTIFICATION
            "screenshot" -> SettingsRoute.PERMISSION_SCREENSHOT
            else -> SettingsRoute.PERMISSION_OVERLAY
        }

        else -> SettingsRoute.LIST
    }

    private fun modelFrom(name: String?): ModelDownloadUi = when (name) {
        "downloading" -> ModelDownloadUi(
            status = ModelDownloadStatus.DOWNLOADING,
            progressPercent = 42,
            downloadedLabel = "12,6",
            totalLabel = "30",
        )

        "ready" -> ModelDownloadUi(status = ModelDownloadStatus.READY)
        "failed" -> ModelDownloadUi(status = ModelDownloadStatus.FAILED)
        else -> ModelDownloadUi(status = ModelDownloadStatus.NOT_DOWNLOADED)
    }
}