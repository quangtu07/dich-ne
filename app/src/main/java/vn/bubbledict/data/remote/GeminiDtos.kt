package vn.bubbledict.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** DTO request/response của Gemini `generateContent` (chỉ field MVP dùng). */
@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(val text: String? = null)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

/** Payload WORD theo `responseSchema` (BE-08). Field thiếu -> mặc định rỗng/null. */
@Serializable
data class WordPayload(
    val meaning: String = "",
    val partOfSpeech: String? = null,
    val ipa: String? = null,
    val examples: List<ExamplePayload> = emptyList(),
    val synonyms: List<String> = emptyList(),
)

@Serializable
data class ExamplePayload(
    val text: String = "",
    val translation: String? = null,
)

/** responseSchema cho chế độ WORD: chỉ `meaning` bắt buộc, còn lại nullable. */
fun wordResponseSchema(): JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("meaning") {
            put("type", "STRING")
            put("description", "Nghĩa tiếng Việt ngắn gọn, có dấu")
        }
        putJsonObject("partOfSpeech") {
            put("type", "STRING")
            put("nullable", true)
            put("description", "Từ loại viết tắt: n, v, adj, adv...")
        }
        putJsonObject("ipa") {
            put("type", "STRING")
            put("nullable", true)
            put("description", "Phiên âm IPA của từ tiếng Anh")
        }
        putJsonObject("examples") {
            put("type", "ARRAY")
            putJsonArray("items") {
                add(
                    buildJsonObject {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("text") { put("type", "STRING") }
                            putJsonObject("translation") {
                                put("type", "STRING")
                                put("nullable", true)
                            }
                        }
                    },
                )
            }
        }
        putJsonObject("synonyms") {
            put("type", "ARRAY")
            putJsonArray("items") { add(buildJsonObject { put("type", "STRING") }) }
        }
    }
    putJsonArray("required") { add("meaning") }
}