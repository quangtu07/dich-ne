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
    private val targetLang: String = "vi",
    /** Công tắc privacy: user tắt "nguồn dịch online" thì chỉ dùng cache/ML Kit. */
    private val onlineAllowed: suspend () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun lookup(
        rawText: String,
        source: TextSource = TextSource.SELECTION,
        sourceApp: String? = null,
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
        val request = LookupRequest(text = text, source = source, sourceApp = sourceApp)
        val kind = request.kind

        // 1) Cache
        cache.read(text, targetLang, kind)?.let { payload ->
            return finish(
                rawText = rawText,
                request = request,
                result = payload.toResult(kind, ResultState.CACHED, Engine.CACHE, truncatedTo),
            )
        }

        // 2) Online (chain keyless -> có key). Tắt online = như nhau "không gọi được online".
        if (!onlineAllowed()) {
            return offlineOrFail(rawText, request, truncatedTo, OFFLINE_ONLY_FAILURE)
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
                cache.write(text, targetLang, kind, payload, engine)
                finish(
                    rawText = rawText,
                    request = request,
                    result = payload.toResult(kind, ResultState.OK, engine, truncatedTo),
                )
            }

            is TranslationOutcome.Translation -> {
                val payload = CachedPayload(translation = outcome.text, provider = outcome.provider)
                val engine = engineFor(outcome.provider)
                cache.write(text, targetLang, kind, payload, engine)
                finish(
                    rawText = rawText,
                    request = request,
                    result = payload.toResult(kind, ResultState.OK, engine, truncatedTo),
                )
            }

            is TranslationOutcome.Failure -> offlineOrFail(rawText, request, truncatedTo, outcome)
        }
    }

    /**
     * Online không cho kết quả -> ML Kit nếu còn dùng được, không thì trả lỗi đúng bản chất.
     *
     * - `IO` (mất mạng) / `NO_KEY` / `NOT_APPLICABLE`: coi như không gọi được online.
     * - `HTTP`/`TIMEOUT` (hết quota, nguồn lỗi): vẫn ưu tiên ML Kit nếu text trong ngưỡng.
     * - `EMPTY`/`NOT_VIETNAMESE`/`PARSE`: nguồn trả rác.
     */
    private suspend fun offlineOrFail(
        rawText: String,
        request: LookupRequest,
        truncatedTo: Int?,
        failure: TranslationOutcome.Failure,
    ): LookupResult {
        val canOffline = OfflinePolicy.canTranslateOffline(request.text)
        if (canOffline && offline.isModelReady()) {
            return mlkitResult(rawText, request, truncatedTo)
        }
        return when {
            // Text ngắn nhưng chưa có gói -> hướng dẫn tải gói (E3a).
            canOffline -> errorResult(rawText, request, LookupErrorCodes.E3A, truncatedTo)
            // Text dài: mất mạng -> E3b "cần mạng"; nguồn lỗi -> E4; nguồn trả rác -> E5.
            failure.reason in NETWORK_ISH -> errorResult(rawText, request, LookupErrorCodes.E3B, truncatedTo)
            failure.reason == FailureReason.QUOTA ->
                errorResult(rawText, request, LookupErrorCodes.E19, truncatedTo)
            failure.reason == FailureReason.HTTP &&
                (failure.httpStatus == 401 || failure.httpStatus == 403) ->
                errorResult(rawText, request, LookupErrorCodes.E20, truncatedTo)
            failure.reason == FailureReason.HTTP ->
                errorResult(rawText, request, LookupErrorCodes.E19, truncatedTo)
            failure.reason in JUNK_ANSWER -> errorResult(rawText, request, LookupErrorCodes.E5, truncatedTo)
            else -> errorResult(rawText, request, LookupErrorCodes.E4, truncatedTo)
        }
    }

    private suspend fun mlkitResult(
        rawText: String,
        request: LookupRequest,
        truncatedTo: Int?,
    ): LookupResult {
        val translated = offline.translate(request.text)
        if (translated.isNullOrBlank()) {
            return errorResult(rawText, request, LookupErrorCodes.E3A, truncatedTo)
        }
        // AC29: hiện kết quả ML Kit NGAY, không chờ enrich từ điển (enrich do [enrichWord]).
        // E22: ML Kit đôi khi trả lại chính tiếng Anh -> không hiện làm nghĩa tiếng Việt.
        val hasVietnamese = looksVietnamese(translated)
        val payload = CachedPayload(
            translation = translated.takeIf { hasVietnamese },
            provider = MLKIT_PROVIDER,
        )
        cache.write(request.text, targetLang, request.kind, payload, Engine.MLKIT)
        return finish(
            rawText = rawText,
            request = request,
            result = payload.toResult(request.kind, ResultState.OK, Engine.MLKIT, truncatedTo)
                .copy(
                    errorCode = LookupErrorCodes.E22.takeIf { !hasVietnamese },
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
            targetLang = targetLang,
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

        /** Nguồn từ điển phụ (dictionaryapi.dev) khi ghép cùng ML Kit cho WORD. */
        const val DICTIONARY_PROVIDER = "dictionaryapi"

        private val VIETNAMESE_CHARS =
            ("ăâđêôơư" +
                "àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ").toSet()

        private val NETWORK_ISH = setOf(
            FailureReason.IO,
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