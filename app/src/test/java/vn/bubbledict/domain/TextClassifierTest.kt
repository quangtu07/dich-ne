package vn.bubbledict.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.bubbledict.domain.model.TextKind

/**
 * BE-04 DoD: ≥16 case cho WORD/SENTENCE/PARAGRAPH, gồm rỗng, khoảng trắng, biên 3/4 token,
 * 1–3 câu, xuống dòng, text 2000 ký tự, ngoặc kép bao ngoài.
 */
class TextClassifierTest {

    private fun kind(raw: String) = TextClassifier.classify(raw)

    // --- rỗng / khoảng trắng ---

    @Test
    fun empty_isWord() = assertEquals(TextKind.WORD, kind(""))

    @Test
    fun blankSpaces_isWord() = assertEquals(TextKind.WORD, kind("   \n\t  "))

    @Test
    fun onlyWrappers_isWord() = assertEquals(TextKind.WORD, kind("  \"\"  "))

    // --- WORD ---

    @Test
    fun oneWord_isWord() = assertEquals(TextKind.WORD, kind("ubiquitous"))

    @Test
    fun twoWords_isWord() = assertEquals(TextKind.WORD, kind("in spite"))

    @Test
    fun threeWords_isBoundaryWord() = assertEquals(TextKind.WORD, kind("in spite of"))

    @Test
    fun fourWords_isSentence() = assertEquals(TextKind.SENTENCE, kind("in spite of this"))

    @Test
    fun wordWithPhraseQuoteWrapper_isWord() = assertEquals(TextKind.WORD, kind("\"ubiquitous\""))

    @Test
    fun wordWrappedInCurlyQuotes_isWord() = assertEquals(TextKind.WORD, kind("“ubiquitous”"))

    @Test
    fun threeWordsWithTrailingColon_isSentence() = assertEquals(TextKind.SENTENCE, kind("note this:"))

    @Test
    fun extraSpacesAndNewlines_doNotChangeKind() =
        assertEquals(TextKind.WORD, kind("  in \n  spite   of  "))

    // --- SENTENCE ---

    @Test
    fun oneSentenceWithPeriod_isSentence() =
        assertEquals(TextKind.SENTENCE, kind("The city was quiet."))

    @Test
    fun questionOneWord_isSentence() = assertEquals(TextKind.SENTENCE, kind("Really?"))

    @Test
    fun quotedSentence_isSentence() =
        assertEquals(TextKind.SENTENCE, kind("“The city was quiet.”"))

    @Test
    fun oneSentenceNoTerminalPunctuation_isSentence() =
        assertEquals(TextKind.SENTENCE, kind("the quick brown fox jumps over the lazy dog"))

    @Test
    fun exclamationSingleSentence_isSentence() =
        assertEquals(TextKind.SENTENCE, kind("What a quiet night!"))

    // --- PARAGRAPH ---

    @Test
    fun twoSentences_isParagraph() =
        assertEquals(TextKind.PARAGRAPH, kind("The city was quiet. Rain fell all night."))

    @Test
    fun threeSentencesOnSeparateLines_isParagraph() = assertEquals(
        TextKind.PARAGRAPH,
        kind("The city was quiet.\nRain fell all night.\nBy morning the streets were empty."),
    )

    @Test
    fun multiLineWithoutSentenceMarks_isParagraph() =
        assertEquals(TextKind.PARAGRAPH, kind("Rain fell all night\nBy morning the streets were empty"))

    @Test
    fun longText2000Chars_isParagraph() {
        val long = "The city was quiet. Rain fell all night. ".repeat(50).trim()
        assertTrue("text phải dài hơn 2000 ký tự, thực tế ${long.length}", long.length > 2_000)
        assertEquals(TextKind.PARAGRAPH, kind(long))
    }

    // --- normalize ---

    @Test
    fun normalize_collapsesAndUnwraps() {
        assertEquals("hello world", NormalizeText.normalize("  Hello \n  World  ").lowercase())
        assertEquals("hello world", NormalizeText.normalize("( \"hello world\" )").lowercase())
        assertEquals("hello.", NormalizeText.normalize(" \"hello.\" "))
    }

    @Test
    fun normalize_stripsCjkQuotes() {
        // 1.0.6 (low CR 1.0.5): ngoặc Nhật/Trung 「」『』〈〉 cũng là wrapper.
        assertEquals("你好。", NormalizeText.normalize("「你好。」"))
        assertEquals("你好。", NormalizeText.normalize("『你好。』"))
        assertEquals("今天天气很好，我们去公园散步。", NormalizeText.normalize("「今天天气很好，我们去公园散步。」"))
    }

    @Test
    fun cjkQuotedSentence_classifiesAsSentence() {
        // 1.0.6: "「你好。」" phải SENTENCE (dấu kết câu thắng độ dài), không phải WORD.
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("「你好。」"))
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("「今天天气很好，我们去公园散步。」"))
    }

    @Test
    fun tokenCount_boundaries() {
        assertEquals(0, NormalizeText.tokenCount(""))
        assertEquals(3, NormalizeText.tokenCount("a b c"))
    }

    // --- BUG-22 / spec 1.25-1.28: phân loại theo script cho CJK/Hangul ---

    @Test
    fun chineseSentence_isSentence() {
        // 13 ký tự Hán, không dấu cách: trước BUG-22 bị 1 token -> WORD.
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("今天天气很好，我们去公园散步。"))
    }

    @Test
    fun chineseThreeChars_isWord() {
        assertEquals(TextKind.WORD, TextClassifier.classify("图书馆"))
    }

    @Test
    fun japaneseSentence_isSentence() {
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("昨日、街の川のそばに新しい図書館ができました。"))
    }

    @Test
    fun koreanSentence_isSentence() {
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("어제 도시는 강 근처에 새 도서관을 열었습니다."))
    }

    @Test
    fun mixedScript_okHao_isSentence() {
        // Spec 1.28/1.30 + Designer: chuỗi lai không phải "một từ" -> SENTENCE.
        // 1.0.6: không còn đếm khoảng trắng; "OK 好" = 3 chữ nhưng là mixed script nên vẫn SENTENCE.
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("OK 好"))
    }

    @Test
    fun mixedScript_abHao_isSentence_neverWord() {
        // 1.0.6: bỏ đếm khoảng trắng -> "ab 好" chỉ còn 3 chữ, nhưng trộn script thì KHÔNG được ra
        // WORD (chip "Từ" hứa có nghĩa + từ loại; chuỗi lai không phải một từ — Designer §9c).
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("ab 好"))
    }

    @Test
    fun mixedScript_longText_paragraphThresholdCountsRealCharsOnly() {
        // 1.0.6: khoảng trắng không được cộng vào ngưỡng paragraph 60 ký tự.
        // "ab " + 54 chữ Hán + " ab" = 58 chữ + 2 space -> mới 58 (SENTENCE); bản 1.0.5 đếm cả space
        // thành 60 nên nhảy PARAGRAPH sớm.
        val text = "ab " + "好".repeat(54) + " ab"
        assertEquals(TextKind.SENTENCE, TextClassifier.classify(text))
    }

    @Test
    fun mixedScript_iphoneChinese_isSentence() {
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("iPhone 手机很好用"))
    }

    @Test
    fun chineseParagraphWithMarks_isParagraph() {
        assertEquals(TextKind.PARAGRAPH, TextClassifier.classify("今天天气很好。我们去公园散步。晚上一起吃饭。"))
    }

    @Test
    fun latinRules_unchanged() {
        // Hồi quy Latin: 1-3 token không dấu kết câu vẫn là WORD.
        assertEquals(TextKind.WORD, TextClassifier.classify("serendipity"))
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("The city was quiet."))
    }

    @Test
    fun cjkPunctuation_beatsLength() {
        // Spec 1.30: "你好。" = 2 chữ Hán + dấu kết câu -> SENTENCE, không phải Từ.
        assertEquals(TextKind.SENTENCE, TextClassifier.classify("你好。"))
        // "苹果" = 2 ký tự, không dấu kết câu -> WORD.
        assertEquals(TextKind.WORD, TextClassifier.classify("苹果"))
    }
}