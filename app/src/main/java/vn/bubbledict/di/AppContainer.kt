package vn.bubbledict.di

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import vn.bubbledict.data.CacheRepository
import vn.bubbledict.data.Maintenance
import vn.bubbledict.data.HistoryRecorder
import vn.bubbledict.data.HistoryRepository
import vn.bubbledict.data.VocabRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.offline.MlkitOfflineTranslator
import vn.bubbledict.data.offline.OfflineModelManager
import vn.bubbledict.data.prefs.EncryptedSecretStore
import vn.bubbledict.data.prefs.SecretStore
import vn.bubbledict.data.prefs.SettingsStore
import vn.bubbledict.data.update.ApkInstaller
import vn.bubbledict.data.update.UpdateChecker
import vn.bubbledict.data.update.UpdateManager
import vn.bubbledict.data.remote.ChainedTranslator
import vn.bubbledict.data.remote.FreeDictionaryApi
import vn.bubbledict.data.remote.GeminiClient
import vn.bubbledict.data.remote.MozhiTranslator
import vn.bubbledict.data.remote.MyMemoryTranslator
import vn.bubbledict.data.remote.OpenAiCompatibleTranslator
import vn.bubbledict.data.remote.OpenAiConfig
import vn.bubbledict.data.offline.OcrEngine
import vn.bubbledict.domain.LookupPipeline
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import vn.bubbledict.BuildConfig
import vn.bubbledict.platform.BubbleController
import vn.bubbledict.platform.NetworkStatus
import vn.bubbledict.platform.TtsSpeaker

/**
 * Nơi duy nhất dựng dependency của app (single module, không dùng DI framework).
 * UI lấy qua `(context.applicationContext as BubbleDictApp).container`.
 *
 * Tất cả `by lazy` nên không tạo DB/keystore/OkHttp cho tới khi thực dùng.
 * Không chứa API key: key chỉ đi qua [SecretStore].
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Scope sống cùng app cho việc dọn dữ liệu nền (BE-17). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(database) }

    val vocabRepository: VocabRepository by lazy { VocabRepository(database) }

    val cacheRepository: CacheRepository by lazy { CacheRepository(database) }

    val settingsStore: SettingsStore by lazy { SettingsStore.create(appContext) }

    val secretStore: SecretStore by lazy { EncryptedSecretStore(appContext) }

    val geminiClient: GeminiClient by lazy {
        GeminiClient(apiKeyProvider = { secretStore.geminiApiKey() })
    }

    /** F14/BE-33: model dịch ML Kit theo từng ngôn ngữ — màn "Gói dịch ngoại tuyến" đọc trạng thái. */
    val offlineModelManager: OfflineModelManager by lazy { OfflineModelManager(settingsStore) }

    val offlineTranslator: MlkitOfflineTranslator by lazy {
        MlkitOfflineTranslator(settingsStore, offlineModelManager)
    }

    /** Nguồn keyless (mặc định chạy được không cần key) — chỉ câu/đoạn. */
    val myMemoryTranslator: MyMemoryTranslator by lazy {
        MyMemoryTranslator(
            // Privacy (spec 1.17 §5.9): không truyền email — MyMemory gọi ẩn danh.
            // BE-20: hết quota hôm nay -> bỏ qua MyMemory tới hết ngày.
            isQuotaBlockedToday = {
                settingsStore.current().keylessQuotaBlockedOn == today()
            },
            markQuotaExhausted = {
                settingsStore.setKeylessQuotaBlockedOn(today())
            },
        )
    }

    private fun today(): String = java.time.LocalDate.now().toString()

    /**
     * Nguồn keyless dự phòng khi MyMemory hết lượt (research 2026-11-04: Mozhi 2 instance còn sống,
     * Lingva/SimplyTranslate bỏ). Chạy sau MyMemory, trước các nguồn có key.
     */
    val mozhiTranslator: MozhiTranslator by lazy {
        MozhiTranslator(logger = { event -> Log.d("Mozhi", event) })
    }

    /** Từ điển keyless cho WORD khi không có key (IPA + định nghĩa tiếng Anh). */
    val dictionaryApi: FreeDictionaryApi by lazy {
        FreeDictionaryApi(logger = { event -> Log.d("FreeDictionary", event) })
    }

    /** Provider OpenAI-compatible (Groq/OpenRouter/Ollama) — chỉ dùng khi user nhập key. */
    val openAiTranslator: OpenAiCompatibleTranslator by lazy {
        OpenAiCompatibleTranslator(
            configProvider = {
                val settings = settingsStore.current()
                OpenAiConfig(
                    apiKey = secretStore.openAiApiKey(),
                    baseUrl = settings.openAiBaseUrl,
                    model = settings.openAiModel,
                )
            },
        )
    }

    /**
     * Chain theo spec 1.5 §5.9: MyMemory (keyless) -> Gemini (nếu có key) -> OpenAI-compatible
     * (nếu có key). Provider không cấu hình tự bị bỏ qua; hết chain thì pipeline rơi tiếp ML Kit.
     */
    val onlineTranslator: ChainedTranslator by lazy {
        ChainedTranslator(
            listOf(myMemoryTranslator, mozhiTranslator, geminiClient, openAiTranslator),
        )
    }

    val historyRecorder: HistoryRecorder by lazy { HistoryRecorder(historyRepository) }

    /** BE-17: dọn cache/lịch sử khi mở app, nút Xoá dữ liệu. */
    val maintenance: Maintenance by lazy {
        Maintenance(
            history = historyRepository,
            cache = cacheRepository,
            vocab = vocabRepository,
            settings = settingsStore,
        )
    }

    /** F2: bật/tắt bong bóng nổi + kiểm quyền overlay/thông báo (BE-13). */
    val bubbleController: BubbleController by lazy { BubbleController(appContext) }

    /** F3: OCR on-device cho vùng chụp màn hình (BE-15). */
    val ocrEngine: OcrEngine by lazy { OcrEngine() }

    /**
     * F12: banner cập nhật — đọc `update.json`, tải APK, xác thực SHA-256 rồi mở trình cài.
     * Dùng OkHttp riêng timeout ngắn: kiểm cập nhật lỗi/mất mạng thì im lặng, không chặn app.
     */
    val updateManager: UpdateManager by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        UpdateManager(
            context = appContext,
            checker = UpdateChecker(client = client, currentVersionCode = BuildConfig.VERSION_CODE),
            client = client,
            installer = ApkInstaller(appContext),
            settingsStore = settingsStore,
            defaultManifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        )
    }

    /** F10: TTS hệ thống ngoài main thread; `speak()` là suspend. */
    val ttsSpeaker: TtsSpeaker by lazy { TtsSpeaker(appContext) }

    /** BUG-16: trạng thái mạng thật — E3a/E3b chỉ khi mất mạng, còn lại E28. */
    val networkStatus: NetworkStatus by lazy { NetworkStatus(appContext) }

    /**
     * Pipeline F1/F2 dùng chung: cache → chain online → ML Kit (O1) → E3a/E3b/E19/E28.
     *
     * F6/F7 (lịch sử + từ vựng) **ra khỏi phạm vi** theo chốt 1.0.4: không truyền [HistoryRecorder]
     * nên pipeline không ghi lịch sử nữa; bảng DB vẫn giữ (không cần migration) và cache vẫn bật
     * để tra lại câu đã dịch nhanh.
     */
    val lookupPipeline: LookupPipeline by lazy {
        LookupPipeline(
            online = onlineTranslator,
            offline = offlineTranslator,
            cache = cacheRepository,
            history = null,
            dictionary = dictionaryApi,
            targetLangProvider = { settingsStore.current().targetLang },
            onlineAllowed = { settingsStore.current().onlineSourcesEnabled },
            networkOnline = { networkStatus.isOnline() },
        )
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}