package com.annie.memento.ui.richtext

import kotlin.test.Test
import kotlin.test.assertEquals

private fun run(text: String, vararg spans: RichSpan, color: Long? = null) =
    RichNode.Run(text, spans.toSet(), color)

private fun ruby(base: String, reading: String, vararg spans: RichSpan, color: Long? = null) =
    RichNode.Ruby(base, reading, spans.toSet(), color)

class RichTextParserTest {

    @Test
    fun plainTextPassesThrough() {
        assertEquals(listOf(run("hello world")), parseRich("hello world").nodes)
    }

    @Test
    fun bracketFuriganaBecomesRuby() {
        assertEquals(listOf(ruby("漢字", "かんじ")), parseRich("漢字[かんじ]").nodes)
    }

    @Test
    fun bracketReadingAttachesToPrecedingTokenOnly() {
        // 食 takes the reading; the trailing okurigana stays plain text.
        assertEquals(listOf(ruby("食", "た"), run("べる")), parseRich("食[た]べる").nodes)
    }

    @Test
    fun textBeforeFuriganaIsSplitAtTheLastSpace() {
        assertEquals(listOf(run("これは "), ruby("漢字", "かんじ")), parseRich("これは 漢字[かんじ]").nodes)
    }

    @Test
    fun spaceBeforeBracketKeepsItLiteral() {
        assertEquals(listOf(run("a [b]")), parseRich("a [b]").nodes)
    }

    @Test
    fun escapedBracketsAreLiteral() {
        assertEquals(listOf(run("[x]")), parseRich("""\[x\]""").nodes)
    }

    @Test
    fun rubyTagSingleSegment() {
        assertEquals(listOf(ruby("漢字", "かんじ")), parseRich("<ruby>漢字<rt>かんじ</rt></ruby>").nodes)
    }

    @Test
    fun rubyTagMultipleSegments() {
        assertEquals(
            listOf(ruby("東", "とう"), ruby("京", "きょう")),
            parseRich("<ruby>東<rt>とう</rt>京<rt>きょう</rt></ruby>").nodes,
        )
    }

    @Test
    fun rubyTagIgnoresRpFallback() {
        assertEquals(
            listOf(ruby("漢", "かん")),
            parseRich("<ruby>漢<rp>(</rp><rt>かん</rt><rp>)</rp></ruby>").nodes,
        )
    }

    @Test
    fun boldWraps() {
        assertEquals(listOf(run("x", RichSpan.BOLD)), parseRich("<b>x</b>").nodes)
    }

    @Test
    fun nestedStylesCombine() {
        assertEquals(
            listOf(run("x", RichSpan.BOLD, RichSpan.ITALIC)),
            parseRich("<b><i>x</i></b>").nodes,
        )
    }

    @Test
    fun strongAndEmAreAliases() {
        assertEquals(
            listOf(run("x", RichSpan.BOLD, RichSpan.ITALIC)),
            parseRich("<strong><em>x</em></strong>").nodes,
        )
    }

    @Test
    fun breakBecomesBreakNode() {
        assertEquals(listOf(run("a"), RichNode.Break, run("b")), parseRich("a<br>b").nodes)
    }

    @Test
    fun unknownTagsAreStrippedButInnerTextKept() {
        assertEquals(listOf(run("abc")), parseRich("a<div>b</div>c").nodes)
    }

    @Test
    fun entitiesAreDecodedAndNotReparsedAsTags() {
        assertEquals(listOf(run("a <b> c")), parseRich("a &lt;b&gt; c").nodes)
    }

    @Test
    fun spanColorIsParsed() {
        assertEquals(listOf(run("x", color = 0xFFFF0000L)), parseRich("""<span style="color:#ff0000">x</span>""").nodes)
    }

    @Test
    fun furiganaCarriesEnclosingStyle() {
        assertEquals(
            listOf(ruby("食", "た", RichSpan.BOLD), run("べる", RichSpan.BOLD)),
            parseRich("<b>食[た]べる</b>").nodes,
        )
    }

    @Test
    fun plainFlatteningDropsReadingsAndTags() {
        assertEquals("食べる", richTextToPlain("<b>食[た]べる</b>"))
        assertEquals("a b", richTextToPlain("a<br>b"))
        assertEquals("東京", richTextToPlain("<ruby>東<rt>とう</rt>京<rt>きょう</rt></ruby>"))
    }
}
