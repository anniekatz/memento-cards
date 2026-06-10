@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.annie.memento.ui.theme.toColor

private const val RUBY_SCALE = 0.5f // size of alt text/furigana above main text

@Composable
fun RichText(
    markup: String,
    rich: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    if (!rich) {
        Text(
            text = markup,
            modifier = modifier,
            color = color,
            style = style,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout,
        )
        return
    }

    val doc = remember(markup) { parseRich(markup) }
    val hasRuby = remember(doc) { doc.nodes.any { it is RichNode.Ruby } }

    if (!hasRuby) {
        // inline styles
        val annotated = remember(doc) {
            buildAnnotatedString {
                doc.nodes.forEach { node ->
                    when (node) {
                        is RichNode.Run -> withStyle(spanStyleOf(node.spans, node.colorArgb)) { append(node.text) }
                        RichNode.Break -> append("\n")
                        is RichNode.Ruby -> append(node.base)
                    }
                }
            }
        }
        Text(
            text = annotated,
            modifier = modifier,
            color = color,
            style = style,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout,
        )
        return
    }

    RubyFlow(doc = doc, style = style, color = color, textAlign = textAlign, modifier = modifier)
}

@Composable
private fun RubyFlow(
    doc: RichDoc,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign?,
    modifier: Modifier,
) {
    val readingStyle = remember(style) {
        style.copy(
            fontSize = if (style.fontSize.isSpecified) style.fontSize * RUBY_SCALE else style.fontSize,
            lineHeight = TextUnit.Unspecified,
        )
    }
    // base glyph at same vertical
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val readingHeight: Dp = remember(readingStyle, density) {
        with(density) { measurer.measure(AnnotatedString("あ"), readingStyle).size.height.toDp() }
    }

    val arrangement = when (textAlign) {
        TextAlign.Center -> Arrangement.Center
        TextAlign.End, TextAlign.Right -> Arrangement.End
        else -> Arrangement.Start
    }

    FlowRow(modifier = modifier, horizontalArrangement = arrangement) {
        doc.nodes.forEach { node ->
            when (node) {
                is RichNode.Run -> {
                    val runStyle = style.applySpans(node.spans, node.colorArgb)
                    val runColor = node.colorArgb?.toColor() ?: color
                    tokenize(node.text).forEach { token ->
                        if (token == "\n") {
                            Spacer(Modifier.fillMaxWidth())
                        } else {
                            Cell(readingHeight) {
                                Text(token, style = runStyle, color = runColor, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                is RichNode.Ruby -> {
                    val baseStyle = style.applySpans(node.spans, node.colorArgb)
                    val rubyColor = node.colorArgb?.toColor() ?: color
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (node.reading.isEmpty()) {
                            Spacer(Modifier.height(readingHeight))
                        } else {
                            Text(node.reading, style = readingStyle, color = rubyColor, maxLines = 1, softWrap = false)
                        }
                        Text(node.base, style = baseStyle, color = rubyColor, maxLines = 1, softWrap = false)
                    }
                }
                RichNode.Break -> Spacer(Modifier.fillMaxWidth())
            }
        }
    }
}

// NON alt text/furigana cell
@Composable
private fun Cell(readingHeight: Dp, base: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(readingHeight))
        base()
    }
}

// latin alphabets... line breaks need to account for whole words
// japanese alphabets, per separation between characters is fine
private fun tokenize(text: String): List<String> {
    val tokens = ArrayList<String>()
    val word = StringBuilder()
    fun flush() {
        if (word.isNotEmpty()) {
            tokens.add(word.toString()); word.clear()
        }
    }
    for (c in text) {
        when {
            c == '\n' -> { flush(); tokens.add("\n") }
            c.isLatinWordChar() -> word.append(c)
            else -> { flush(); tokens.add(c.toString()) }
        }
    }
    flush()
    return tokens
}

private fun Char.isLatinWordChar(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' ||
        this == '\'' || this == '’' || this == '-' || this in 'À'..'ÿ'

private fun spanStyleOf(spans: Set<RichSpan>, colorArgb: Long?): SpanStyle = SpanStyle(
    color = colorArgb?.toColor() ?: Color.Unspecified,
    fontWeight = if (RichSpan.BOLD in spans) FontWeight.Bold else null,
    fontStyle = if (RichSpan.ITALIC in spans) FontStyle.Italic else null,
    textDecoration = if (RichSpan.UNDERLINE in spans) TextDecoration.Underline else null,
)

private fun TextStyle.applySpans(spans: Set<RichSpan>, colorArgb: Long?): TextStyle =
    merge(
        TextStyle(
            color = colorArgb?.toColor() ?: Color.Unspecified,
            fontWeight = if (RichSpan.BOLD in spans) FontWeight.Bold else null,
            fontStyle = if (RichSpan.ITALIC in spans) FontStyle.Italic else null,
            textDecoration = if (RichSpan.UNDERLINE in spans) TextDecoration.Underline else null,
        ),
    )
