package vn.bubbledict.ui.result

import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * Dữ liệu mẫu cho màn dev/preview và screenshot QA. Không dùng trong luồng thật.
 * Text lấy từ ngữ cảnh sách/PDF tiếng Anh, không phải dữ liệu người dùng.
 */
object ResultPreviewData {

    val sampleWord = ResultUiState(
        status = ResultStatus.SUCCESS,
        kind = TextKind.WORD,
        originalText = "comprehension",
        ipa = "/ˌkɒmprɪˈhenʃn/",
        senses = listOf(
            SenseUi(index = 1, meaning = "sự hiểu, sự lĩnh hội", partOfSpeech = "n"),
            SenseUi(index = 2, meaning = "năng lực hiểu"),
        ),
        examples = listOf(
            ExampleUi(
                text = "beyond comprehension",
                translation = "ngoài tầm hiểu",
            ),
        ),
        synonyms = listOf("understanding", "grasp"),
    )

    val sampleSentence = ResultUiState(
        status = ResultStatus.SUCCESS,
        kind = TextKind.SENTENCE,
        originalText = "Reading English books is a great way to learn.",
        translation = "Đọc sách tiếng Anh là một cách tuyệt vời để học.",
    )

    val sampleParagraph = ResultUiState(
        status = ResultStatus.SUCCESS,
        kind = TextKind.PARAGRAPH,
        originalText = "Reading English books is a great way to learn. It builds vocabulary naturally. You also get used to real sentence structures.",
        translation = "Đọc sách tiếng Anh là một cách tuyệt vời để học. Việc này xây dựng vốn từ một cách tự nhiên. Bạn cũng quen dần với cấu trúc câu thật.",
    )

    val sampleOcr = ResultUiState(
        status = ResultStatus.SUCCESS,
        kind = TextKind.WORD,
        source = TextSource.OCR,
        originalText = "comprehension",
        ipa = "/ˌkɒmprɪˈhenʃn/",
        senses = listOf(
            SenseUi(index = 1, meaning = "sự hiểu, sự lĩnh hội", partOfSpeech = "n"),
        ),
        examples = listOf(
            ExampleUi(text = "beyond comprehension", translation = "ngoài tầm hiểu"),
        ),
    )

    val sampleLong = ResultUiState(
        status = ResultStatus.SUCCESS,
        kind = TextKind.PARAGRAPH,
        originalText = "Reading English books is a great way to learn because it exposes you to new words in context, helps you see how grammar works in real sentences, and keeps you interested in the story. Over time, this habit builds vocabulary naturally without flashcards. You also get used to real sentence structures, which makes writing and speaking feel easier.",
        translation = "Đọc sách tiếng Anh là một cách tuyệt vời để học vì nó giúp bạn gặp từ mới trong ngữ cảnh, thấy ngữ pháp hoạt động trong câu thật và giữ bạn hứng thú với câu chuyện. Theo thời gian, thói quen này xây dựng vốn từ một cách tự nhiên mà không cần flashcard. Bạn cũng quen dần với cấu trúc câu thật, khiến việc viết và nói trở nên dễ hơn.",
    )

    /** Map tên state (intent extra) sang model; dùng cho screenshot 10 state S1–S10. */
    fun forState(name: String): ResultUiState = when (name.uppercase()) {
        "S1", "LOADING" -> sampleWord.copy(status = ResultStatus.LOADING)
        "S2", "SUCCESS" -> sampleWord
        "S3", "CACHED" -> sampleWord.copy(
            status = ResultStatus.CACHED,
            cachedAtLabel = "14:03 hôm nay",
        )
        "S4", "OFFLINE_RESULT" -> sampleWord.copy(
            status = ResultStatus.OFFLINE_RESULT,
            senses = listOf(SenseUi(index = 1, meaning = "sự hiểu, sự lĩnh hội")),
            examples = emptyList(),
            synonyms = emptyList(),
        )
        "S5", "ERROR_NO_NETWORK_SHORT" -> sampleWord.copy(status = ResultStatus.ERROR_NO_NETWORK_SHORT)
        "S6", "ERROR_NO_NETWORK_LONG" -> sampleParagraph.copy(status = ResultStatus.ERROR_NO_NETWORK_LONG)
        "S7", "ERROR_SOURCE" -> sampleWord.copy(status = ResultStatus.ERROR_SOURCE)
        "S8", "ERROR_EMPTY" -> sampleWord.copy(status = ResultStatus.ERROR_EMPTY)
        "S9", "TRUNCATED" -> sampleLong.copy(
            status = ResultStatus.TRUNCATED,
            truncatedPrefixLength = 2000,
        )
        "S10", "WORD_NOT_FOUND" -> sampleWord.copy(
            status = ResultStatus.WORD_NOT_FOUND,
            ipa = null,
            senses = emptyList(),
            examples = emptyList(),
            synonyms = emptyList(),
            translation = "Đây là bản dịch câu cho từ không có trong từ điển.",
        )
        "SENTENCE" -> sampleSentence
        "PARAGRAPH", "LONG" -> sampleParagraph
        "OCR" -> sampleOcr
        "EDITING", "OCR_REVIEW" -> sampleOcr.copy(
            editing = true,
            editText = "comprehenshion",
        )
        "S11", "DOWNLOADING_MODEL" -> sampleWord.copy(status = ResultStatus.DOWNLOADING_MODEL)
        // AC28: WORD không key — ML Kit nghĩa tiếng Việt + IPA + tối đa 2 nghĩa tiếng Anh.
        // Text gốc, IPA, nghĩa và định nghĩa phải cùng MỘT từ (Designer bắt lỗi ảnh trước).
        "S4_EN", "AC28" -> sampleWord.copy(
            status = ResultStatus.OFFLINE_RESULT,
            originalText = "ubiquitous",
            ipa = "/juːˈbɪk.wə.təs/",
            senses = listOf(SenseUi(index = 1, meaning = "có mặt khắp nơi, ở đâu cũng có")),
            examples = emptyList(),
            synonyms = emptyList(),
            englishDefinitions = listOf(
                "Being everywhere at once: omnipresent.",
                "Seeming to be everywhere at the same time.",
            ),
        )
        // E22: ML Kit trả tiếng Anh -> không hiện làm nghĩa, chỉ hiện định nghĩa EN.
        "E22" -> sampleWord.copy(
            originalText = "lucid",
            ipa = "/ˈl(j)uːsɪd/",
            status = ResultStatus.OFFLINE_RESULT,
            translation = null,
            senses = emptyList(),
            examples = emptyList(),
            synonyms = emptyList(),
            noVietnameseMeaning = true,
            englishDefinitions = listOf("Expressed clearly; easy to understand."),
        )
        "E22_EMPTY" -> sampleWord.copy(
            originalText = "lucid",
            status = ResultStatus.OFFLINE_RESULT,
            translation = null,
            ipa = null,
            senses = emptyList(),
            examples = emptyList(),
            synonyms = emptyList(),
            noVietnameseMeaning = true,
            englishDefinitions = emptyList(),
        )
        // Designer: nhãn loại từ tiếng Việt (không trộn tiếng Anh trong câu nghĩa).
        "POS" -> sampleWord.copy(
            originalText = "magnanimous",
            ipa = "/mæɡˈnæn.ɪ.məs/",
            senses = listOf(
                SenseUi(index = 1, meaning = "hào hùng, cao thượng", partOfSpeech = "tính từ"),
                SenseUi(index = 2, meaning = "rộng lượng", partOfSpeech = "tính từ"),
            ),
            examples = emptyList(),
            synonyms = emptyList(),
            englishDefinitions = listOf("Noble and generous in spirit."),
        )
        // E19/AC25: hết quota keyless -> gợi ý nhập key.
        "S12", "ERROR_QUOTA" -> sampleParagraph.copy(status = ResultStatus.ERROR_QUOTA)
        // E20: key sai 401/403.
        "S13", "ERROR_KEY_INVALID" -> sampleParagraph.copy(status = ResultStatus.ERROR_KEY_INVALID)
        "UK_MISSING" -> sampleWord.copy(ukAvailable = false, voice = Voice.UK)
        "TTS_MISSING" -> sampleWord.copy(ttsReady = false, ukAvailable = false, usAvailable = false)
        "SAVED" -> sampleWord.copy(isSaved = true)
        else -> sampleWord
    }
}