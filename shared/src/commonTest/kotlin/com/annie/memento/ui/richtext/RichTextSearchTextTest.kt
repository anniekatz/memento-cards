package com.annie.memento.ui.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextSearchTextTest {

    @Test
    fun plainMarkupStaysSingleVariant() {
        assertEquals("hello world", richTextToSearchText("hello world"))
    }

    @Test
    fun stripsFormattingTags() {
        assertEquals("bold and plain", richTextToSearchText("<b>bold</b> and plain"))
    }

    @Test
    fun bracketFuriganaYieldsBaseAndReadingVariants() {
        assertEquals("漢字\nかんじ", richTextToSearchText("漢字[かんじ]"))
    }

    @Test
    fun perCharacterFuriganaReadingsJoinUp() {
        // search furigana/alt text
        val searchable = richTextToSearchText("勉[べん]強[きょう]する")
        assertTrue(searchable.contains("勉強する"))
        assertTrue(searchable.contains("べんきょうする"))
    }

    @Test
    fun adjacentRubyTagsFromAnkiImportJoinUp() {
        val searchable = richTextToSearchText("<ruby>勉<rt>べん</rt></ruby><ruby>強<rt>きょう</rt></ruby>する")
        assertTrue(searchable.contains("勉強する"))
        assertTrue(searchable.contains("べんきょうする"))
    }

    @Test
    fun rubyTagsYieldBaseAndReadingVariants() {
        assertEquals("勉\nべん", richTextToSearchText("<ruby>勉<rt>べん</rt></ruby>"))
    }

    @Test
    fun mixedRunsAndRubiesKeepBothVariantsReadable() {
        val searchable = richTextToSearchText("これは 漢字[かんじ] です")
        assertTrue(searchable.contains("これは 漢字 です"))
        assertTrue(searchable.contains("これは かんじ です"))
    }
}
