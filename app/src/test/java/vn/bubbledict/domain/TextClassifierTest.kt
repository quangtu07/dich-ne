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
    fun tokenCount_boundaries() {
        assertEquals(0, NormalizeText.tokenCount(""))
        assertEquals(3, NormalizeText.tokenCount("a b c"))
    }
}