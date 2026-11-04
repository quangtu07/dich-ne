package vn.bubbledict.domain

import vn.bubbledict.data.remote.FailureReason
import vn.bubbledict.data.remote.TranslationOutcome
import vn.bubbledict.domain.model.CachedPayload
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.Example
import vn.bubbledict.domain.model.LookupRequest
import vn.bubbledict.domain.model.LookupResult
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.Sense
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/** Mã lỗi spec §5.1 dùng trong [LookupResult.errorCode]. */
object LookupErrorCodes {
    const val E1 = "E1"
    const val E3A = "E3a"
    const val E3B = "E3b"
    const val E4 = "E4"
    const val E5 = "E5"
    /** E19: hết lượt dịch miễn phí (keyless quota) — KHÔNG báo "cần mạng". */
    const val E19 = "E19"
    /** E20: API key không hợp lệ (401/403) — rơi về keyless/ML Kit trước khi báo. */
    const val E20 = "E20"
    /** E22: ML Kit trả output không phải tiếng Việt (soft: vẫn có IPA/định nghĩa EN nếu enrich được). */
    const val E22 = "E22"
    /**
     * E28 (spec 1.16, BUG-16): máy **có mạng** nhưng cả chain keyless hỏng hạ tầng
     * (5xx/timeout/IO) — "Nguồn dịch miễn phí tạm không dùng được, thử lại sau".
     * KHÔNG dùng copy "Bật mạng" (E3a/E3b) và không dùng copy "Hết lượt" (E19).
     */
    const val E28 = "E28"
    /**
     * E3C (spec 1.21, BE-36): máy **có mạng** nhưng thiếu model offline cho tra từ / không có
     * nguồn online phù hợp (WORD không đi nguồn keyless nào) — "Chưa tải gói offline để tra từ
     * (≈30MB)" + nút tải gói. KHÁC E3a (mất mạng thật) và khác E28 (nguồn hỏng).
     * 1.0.4 tạm trả E3a với copy đúng (nợ đã duyệt); từ 1.0.5 trả đúng E3c.
     */
    const val E3C = "E3c"
    /**
     * E30 (spec 1.21/F14): thiếu model ML Kit cho **ngôn ngữ đích** (1.0.5 dùng).
     * UI dùng lại layout S5, chuỗi "Chưa tải gói ngôn ngữ này (≈30MB)".
     */
    const val E30 = "E30"
    /**
     * E31 (F14): cặp ngôn ngữ ngoài phạm vi 5 ngôn ngữ hỗ trợ — UI dùng S7 + nút "Mở cài đặt".
     */
    const val E31 = "E31"
}

/**
 * BE-10 — thang dịch theo chốt của Chief (ưu tiên **chạy được khi không có key**):
 *
 * 1. Cache (text chuẩn hoá + targetLang + kind) -> (CACHED, CACHE), không gọi mạng.
 * 2. Online theo chain ([OnlineTranslator], thường là `ChainedTranslator`):
 *    MyMemory keyless (câu/đoạn) -> provider có key (Gemini/Groq, WORD + chất lượng cao).
 * 3. Nếu online không dùng được (mất mạng / hết quota / chưa cấu hình / tắt nguồn online)
 *    -> ML Kit on-device khi text ≤200 ký tự và ≤3 câu (O1) và model đã tải -> (OK, MLKIT).
 * 4. Hết đường -> lỗi đúng bản chất: E3a (chưa tải gói), E3b (cần mạng), E4 (nguồn lỗi), E5.
 *
 * Yêu cầu mới (AC24): đoạn dài không key + có mạng vẫn dịch được nhờ bước 2 (MyMemory).
 * E2: text > 2000 ký tự thì cắt phần đầu, vẫn tra bình thường và ghi [LookupResult.truncatedTo].
 * Mọi lượt (kể cả lỗi) ghi lịch sử qua [HistorySink].
 */
class LookupPipeline(
    private val online: OnlineTranslator,
    private val offline: OfflineTranslator,
    private val cache: LookupCache,
    private val history: HistorySink? = null,
    /** Bổ sung IPA/định nghĩa cho WORD khi không có key (dictionaryapi.dev); null = tắt. */
    private val dictionary: DictionaryEnricher? = null,
    /** F14/BE-34: ngôn ngữ đích lấy động từ Settings (mặc định "vi" giữ hành vi 1.0.4). */
    private val targetLangProvider: suspend () -> String = { "vi" },
    /** Công tắc privacy: user tắt "nguồn dịch online" thì chỉ dùng cache/ML Kit. */
    private val onlineAllowed: suspend () -> Boolean = { true },
    /**
     * BUG-16: máy có mạng thật hay không, để chọn đúng câu lỗi — có mạng mà nguồn chết thì
     * báo E28 ("nguồn tạm không dùng được"), chỉ khi mất mạng mới báo E3a/E3b ("Bật mạng").
     * Mặc định `true` để unit test không cần Android; nơi dựng app truyền `NetworkStatus`.
     */
    private val networkOnline: suspend () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * F14 (BE-34): nhận diện ngôn ngữ nguồn + lấy ngôn ngữ đích từ Settings, rồi gắn cặp vào kết quả.
     *
     * - **Nguồn = đích** (nhận diện chắc) -> trả nguyên văn, **không gọi API** (UX-06: tránh đốt quota).
     * - Cặp ngoài 5 ngôn ngữ hỗ trợ -> **E31**.
     * - Không chắc ngôn ngữ nguồn -> vẫn tra, cờ `sourceLangUncertain` cho UI hiện chip `? → VI` (E29).
     */
    suspend fun lookup(
        rawText: String,
        source: TextSource = TextSource.SELECTION,
        sourceApp: String? = null,
    ): LookupResult {
        val targetLang = runCatching { targetLangProvider() }.getOrDefault(LanguageDetector.VI).lowercase()
        val normalized = NormalizeText.normalize(rawText)
        val detection = LanguageDetector.detect(normalized)
        val sourceLang = detection.lang.ifBlank { LanguageDetector.EN }

        // Cặp ngoài phạm vi hỗ trợ (E31, S7 + nút Mở cài đặt).
        if (targetLang !in LanguageDetector.SUPPORTED) {
            val request = LookupRequest(
                text = normalized.ifBlank { " " },
                source = source,
                sourceApp = sourceApp,
                sourceLang = sourceLang,
                targetLang = targetLang,
            )
            return errorResult(rawText, request, LookupErrorCodes.E31)
                .copy(sourceLang = sourceLang, targetLang = targetLang, sourceLangUncertain = detection.uncertain)
        }

        // Nguồn = đích: trả nguyên văn, không gọi API (UX-06 §3).
        if (normalized.isNotBlank() && !detection.uncertain && detection.lang == targetLang) {
            val truncatedTo = normalized.length.takeIf { it > MAX_TEXT_CHARS }?.let { MAX_TEXT_CHARS }
            val text = if (truncatedTo != null) normalized.take(MAX_TEXT_CHARS) else normalized
            val request = LookupRequest(
                text = text,
                source = source,
                sourceApp = sourceApp,
                sourceLang = sourceLang,
                targetLang = targetLang,
            )
            return finish(
                rawText = rawText,
                request = request,
                result = LookupResult(
                    kind = request.kind,
                    state = ResultState.OK,
                    engine = Engine.CACHE,
                    translation = text,
                    provider = SAME_LANGUAGE_PROVIDER,
                    requestText = text,
                    truncatedTo = truncatedTo,
                    createdAt = now(),
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                ),
            )
        }

        return lookupWithLangs(rawText, source, sourceApp, sourceLang, targetLang)
            .copy(
                sourceLang = sourceLang,
                targetLang = targetLang,
                sourceLangUncertain = detection.uncertain,
            )
    }

    /** Thân tra cũ (1.0.4) + cặp ngôn ngữ (F14). */
    private suspend fun lookupWithLangs(
        rawText: String,
        source: TextSource,
        sourceApp: String?,
        sourceLang: String,
        targetLang: String,
    ): LookupResult {
        val normalized = NormalizeText.normalize(rawText)
        if (normalized.isBlank()) {
            return errorResult(
                request = LookupRequest(text = " ", source = source, sourceApp = sourceApp),
                rawText = rawText,
                code = LookupErrorCodes.E1,
            )
        }

        val truncatedTo = normalized.length.takeIf { it > MAX_TEXT_CHARS }?.let { MAX_TEXT_CHARS }
        val text = if (truncatedTo != null) normalized.take(MAX_TEXT_CHARS) else normalized
        val request = LookupRequest(
            text = text,
            source = source,
            sourceApp = sourceApp,
            sourceLang = sourceLang,
            targetLang = targetLang,
        )
        val kind = request.kind
        val cacheKey = cacheKey(sourceLang, targetLang)

        // 1) Cache
        cache.read(text, cacheKey, kind)?.let { payload ->
            return finish(
                rawText = rawText,
                request = request,
                result = payload.toResult(kind, ResultState.CACHED, Engine.CACHE, truncatedTo),
            )
        }

        // 2) Online (chain keyless -> có key). Tắt online = như nhau "không gọi được online".
        if (!onlineAllowed()) {
            return offlineOrFail(rawText, request, truncatedTo, OFFLINE_ONLY_FAILURE, sourceLang, targetLang)
        }
        return when (val outcome = online.lookup(request)) {
            is TranslationOutcome.Word -> {
                val payload = CachedPayload(
                    senses = outcome.payload.senses(),
                    ipa = outcome.payload.ipa,
                    examples = outcome.payload.examples(),
                    synonyms = outcome.payload.synonyms,
                    provider = outcome.provider,
                )
                val engine = engineFor(outcome.provider)
                cache.write(text, cacheKey, kind, payload, engine)
                finish(
                    rawText = rawText,
                    request = request,
                    result = payload.toResult(kind, ResultState.OK, engine, truncatedTo),
                )
            }

            is TranslationOutcome.Translation -> {
                val payload = CachedPayload(translation = outcome.text, provider = outcome.provider)
                val engine = engineFor(outcome.provider)
                cache.write(text, cacheKey, kind, payload, engine)
                finish(
                    rawText = rawText,
                    request = request,
                    result = payload.toResult(kind, ResultState.OK, engine, truncatedTo),
                )
            }

            is TranslationOutcome.Failure ->
                offlineOrFail(rawText, request, truncatedTo, outcome, sourceLang, targetLang)
        }
    }

    /**
     * Online không cho kết quả -> ML Kit nếu còn dùng được, không thì trả lỗi đúng bản chất.
     *
     * Spec 1.16 / BUG-16 tách 3 nhánh lỗi:
     * - **E3a/E3b** = máy thật sự mất mạng (hoặc user tắt nguồn online) -> mới được nói "Bật mạng".
     * - **E19** = hết lượt miễn phí: chain trả `QUOTA` (mọi nguồn keyless đều quota).
     * - **E28** = có mạng nhưng nguồn keyless hỏng hạ tầng (5xx/timeout/IO/rác) -> "tạm không
     *   dùng được, thử lại sau". Không báo "Bật mạng", không báo "hết lượt".
     */
    private suspend fun offlineOrFail(
        rawText: String,
        request: LookupRequest,
        truncatedTo: Int?,
        failure: TranslationOutcome.Failure,
        sourceLang: String,
        targetLang: String,
    ): LookupResult {
        val canOffline = OfflinePolicy.canTranslateOffline(request.text)
        val modelReady = canOffline && offline.isModelReady(sourceLang, targetLang)
        if (modelReady) {
            return mlkitResult(rawText, request, truncatedTo, sourceLang, targetLang)
        }
        // BUG-16: câu "Bật mạng" chỉ đúng khi máy thật sự mất mạng; lỗi đọc trạng thái mạng thì
        // coi như có mạng (an toàn hơn: không hướng dẫn user bật mạng vô cớ).
        val hasNetwork = runCatching { networkOnline() }.getOrDefault(true)
        val offlineHint = if (canOffline) LookupErrorCodes.E3A else LookupErrorCodes.E3B
        // F14: cặp khác en→vi cần model riêng -> E30 cụ thể hơn E3c (Designer/UX-06 §10).
        // Cặp mặc định en→vi giữ nguyên E3c/E3a (AC38 của 1.0.4/1.0.5).
        val nonDefaultPair = sourceLang != LanguageDetector.EN || targetLang != LanguageDetector.VI
        return when {
            // Spec 1.28: mọi nguồn từ chối cặp -> E31 (kể cả khi thiếu model), không phải E30/E28.
            failure.reason == FailureReason.PAIR_UNSUPPORTED ->
                errorResult(rawText, request, LookupErrorCodes.E31, truncatedTo)
            nonDefaultPair && canOffline && hasNetwork ->
                errorResult(rawText, request, LookupErrorCodes.E30, truncatedTo)
            // Chưa từng gọi online được (user tắt công tắc online / chưa cấu hình key / WORD
            // không có nguồn keyless phù hợp): có mạng mà thiếu model -> E3c "tải gói offline",
            // mất mạng thật -> E3a.
            failure.reason in NEVER_TRIED_ONLINE -> when {
                !canOffline -> errorResult(rawText, request, LookupErrorCodes.E3B, truncatedTo)
                hasNetwork -> errorResult(rawText, request, LookupErrorCodes.E3C, truncatedTo)
                else -> errorResult(rawText, request, LookupErrorCodes.E3A, truncatedTo)
            }
            // Key sai -> E20 (dễ hành động: mở Settings nhập lại key).
            failure.reason == FailureReason.HTTP &&
                (failure.httpStatus == 401 || failure.httpStatus == 403) ->
                errorResult(rawText, request, LookupErrorCodes.E20, truncatedTo)
            // Mất mạng thật -> hướng dẫn bật mạng / tải gói offline (E3a/E3b).
            !hasNetwork -> errorResult(rawText, request, offlineHint, truncatedTo)
            // Spec 1.20/AC38: WORD không bao giờ ra E19/E28 — có mạng mà chưa có model thì
            // hướng dẫn tải gói offline (E3c), không báo "nguồn hỏng"/"hết lượt".
            request.kind == TextKind.WORD ->
                errorResult(rawText, request, LookupErrorCodes.E3C, truncatedTo)
            // Hết lượt thật (mọi nguồn keyless trả QUOTA) -> E19, không nói "cần mạng" (AC25a).
            failure.reason == FailureReason.QUOTA ->
                errorResult(rawText, request, LookupErrorCodes.E19, truncatedTo)
            // Có mạng: nguồn trả rác -> E5.
            failure.reason in JUNK_ANSWER -> errorResult(rawText, request, LookupErrorCodes.E5, truncatedTo)
            // Có mạng: nguồn tạm hỏng hạ tầng (5xx/timeout/IO/HTTP khác) -> E28 (BUG-16).
            else -> errorResult(rawText, request, LookupErrorCodes.E28, truncatedTo)
        }
    }

    private suspend fun mlkitResult(
        rawText: String,
        request: LookupRequest,
        truncatedTo: Int?,
        sourceLang: String,
        targetLang: String,
    ): LookupResult {
        val translated = offline.translate(request.text, sourceLang, targetLang)
        if (translated.isNullOrBlank()) {
            return errorResult(rawText, request, LookupErrorCodes.E3A, truncatedTo)
        }
        // AC29: hiện kết quả ML Kit NGAY, không chờ enrich từ điển (enrich do [enrichWord]).
        // E22 (spec 1.26/BE-38): chỉ chặn output khi **đích là tiếng Việt** và output không có
        // ký tự Việt. Đích EN/ZH/KO/JA thì mọi output khác rỗng đều hợp lệ — không được xoá
        // bản dịch (CR-11: trước đây đích ≠ VI bị E22 oan).
        val vietnameseTarget = request.targetLang.equals("vi", ignoreCase = true)
        val validAnswer = if (vietnameseTarget) looksVietnamese(translated) else translated.isNotBlank()
        val payload = CachedPayload(
            translation = translated.takeIf { validAnswer },
            provider = MLKIT_PROVIDER,
        )
        cache.write(
            request.text,
            cacheKey(sourceLang, targetLang),
            request.kind,
            payload,
            Engine.MLKIT,
        )
        return finish(
            rawText = rawText,
            request = request,
            result = payload.toResult(request.kind, ResultState.OK, Engine.MLKIT, truncatedTo)
                .copy(
                    errorCode = LookupErrorCodes.E22.takeIf { vietnameseTarget && !validAnswer },
                    requestText = request.text,
                ),
        )
    }

    /**
     * AC29 — enrich IPA/định nghĩa tiếng Anh **sau khi** sheet đã hiện nghĩa ML Kit.
     * FE gọi hàm này sau `lookup()` và cập nhật state khi có kết quả; null = không có gì để cập nhật (E21).
     * Chỉ dùng cho WORD; chỉ gửi đúng từ đó.
     */
    suspend fun enrichWord(result: LookupResult): LookupResult? {
        if (result.kind != TextKind.WORD || result.state != ResultState.OK) return null
        if (!onlineAllowed()) return null
        val word = result.requestText.ifBlank { result.translation.orEmpty() }
        if (word.isBlank()) return null
        val info = dictionary?.enrich(word) ?: return null
        if (info.ipa == null && info.definitions.isEmpty()) return null

        val merged = result.copy(
            ipa = info.ipa,
            senses = result.senses.map { it.copy(partOfSpeech = it.partOfSpeech ?: info.partOfSpeech) },
            englishDefinitions = info.definitions,
            synonyms = if (result.synonyms.isEmpty()) info.synonyms else result.synonyms,
            provider = "$MLKIT_PROVIDER+$DICTIONARY_PROVIDER",
        )
        // Cập nhật cache để lượt sau có luôn IPA/định nghĩa.
        cache.write(
            normText = result.requestText,
            targetLang = cacheKey(result.sourceLang, result.targetLang),
            kind = result.kind,
            payload = CachedPayload(
                translation = merged.translation,
                senses = merged.senses,
                ipa = merged.ipa,
                synonyms = merged.synonyms,
                englishDefinitions = merged.englishDefinitions,
                provider = merged.provider,
            ),
            engine = Engine.MLKIT,
        )
        // BUG-08: lịch sử đã ghi từ lúc render (provider=mlkit) -> cập nhật nguồn thật.
        history?.updateProvider(
            normText = result.requestText,
            createdAt = result.createdAt,
            provider = merged.provider.orEmpty(),
        )
        return merged
    }

    private suspend fun errorResult(
        rawText: String,
        request: LookupRequest,
        code: String,
        truncatedTo: Int? = null,
    ): LookupResult = finish(
        rawText = rawText,
        request = request,
        result = LookupResult(
            kind = request.kind,
            state = ResultState.ERROR,
            engine = null,
            errorCode = code,
            truncatedTo = truncatedTo,
            createdAt = now(),
        ),
    )

    private suspend fun finish(
        rawText: String,
        request: LookupRequest,
        result: LookupResult,
    ): LookupResult {
        history?.record(rawText = rawText, request = request, result = result)
        return result
    }

    /** Khoá cache theo **cặp ngôn ngữ** (F14/E31) — tránh trả bản dịch VI cho lượt đích EN. */
    private fun cacheKey(sourceLang: String, targetLang: String): String = "$sourceLang>$targetLang"

    private fun looksVietnamese(text: String): Boolean =
        text.any { it.lowercaseChar() in VIETNAMESE_CHARS }

    /** `mymemory` là nguồn keyless; còn lại (gemini/openai-compatible) là nguồn có key. */
    private fun engineFor(provider: String?): Engine =
        if (provider == "mymemory") Engine.KEYLESS else Engine.KEY

    private fun CachedPayload.toResult(
        kind: vn.bubbledict.domain.model.TextKind,
        state: ResultState,
        engine: Engine,
        truncatedTo: Int?,
    ) = LookupResult(
        kind = kind,
        state = state,
        engine = engine,
        translation = translation,
        senses = senses,
        ipa = ipa,
        examples = examples,
        synonyms = synonyms,
        englishDefinitions = englishDefinitions,
        provider = provider,
        truncatedTo = truncatedTo,
        createdAt = now(),
    )

    private fun vn.bubbledict.data.remote.WordPayload.senses(): List<Sense> =
        listOf(Sense(meaning = meaning, partOfSpeech = partOfSpeech))

    private fun vn.bubbledict.data.remote.WordPayload.examples(): List<Example> =
        examples.map { Example(text = it.text, translation = it.translation) }

    companion object {
        /** Trần 1 lượt tra (E2 / chống bội chi API). */
        const val MAX_TEXT_CHARS = 2000

        /** Provider ghi cho kết quả on-device. */
        const val MLKIT_PROVIDER = "mlkit"

        /** F14: nguồn = đích -> trả nguyên văn, không gọi API (UX-06 §3). */
        const val SAME_LANGUAGE_PROVIDER = "same-language"

        /** Nguồn từ điển phụ (dictionaryapi.dev) khi ghép cùng ML Kit cho WORD. */
        const val DICTIONARY_PROVIDER = "dictionaryapi"

        private val VIETNAMESE_CHARS =
            ("ăâđêôơư" +
                "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ").toSet()

        /** Chain không thử online (tắt công tắc / chưa cấu hình nguồn) — nhánh offline thuần. */
        private val NEVER_TRIED_ONLINE = setOf(
            FailureReason.NO_KEY,
            FailureReason.NOT_APPLICABLE,
        )

        private val JUNK_ANSWER = setOf(
            FailureReason.EMPTY,
            FailureReason.NOT_VIETNAMESE,
            FailureReason.PARSE,
        )

        private val OFFLINE_ONLY_FAILURE =
            TranslationOutcome.Failure("E3", FailureReason.NO_KEY)
    }
}