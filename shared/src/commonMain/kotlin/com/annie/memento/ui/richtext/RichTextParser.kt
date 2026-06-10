package com.annie.memento.ui.richtext


//rich text parsing
internal enum class RichSpan { BOLD, ITALIC, UNDERLINE }

internal sealed interface RichNode {
    data class Run(val text: String, val spans: Set<RichSpan>, val colorArgb: Long?) : RichNode

    data class Ruby(val base: String, val reading: String, val spans: Set<RichSpan>, val colorArgb: Long?) : RichNode

    data object Break : RichNode
}

internal data class RichDoc(val nodes: List<RichNode>)

internal fun parseRich(markup: String): RichDoc {
    val out = ArrayList<RichNode>()
    val buf = StringBuilder()

    var bold = 0
    var italic = 0
    var underline = 0
    val colorStack = ArrayDeque<Long?>()

    fun spans(): Set<RichSpan> = buildSet {
        if (bold > 0) add(RichSpan.BOLD)
        if (italic > 0) add(RichSpan.ITALIC)
        if (underline > 0) add(RichSpan.UNDERLINE)
    }
    fun color(): Long? = colorStack.lastOrNull { it != null }

    fun flush() {
        if (buf.isNotEmpty()) {
            out.add(RichNode.Run(buf.toString(), spans(), color()))
            buf.clear()
        }
    }

    val n = markup.length
    var i = 0
    while (i < n) {
        val c = markup[i]
        when {
            // escapes
            c == '\\' && i + 1 < n && markup[i + 1] in "[]<>&\\" -> {
                buf.append(markup[i + 1]); i += 2
            }

            // entities
            c == '&' -> {
                val semi = markup.indexOf(';', i)
                val decoded = if (semi != -1 && semi - i <= 7) decodeEntity(markup.substring(i, semi + 1)) else null
                if (decoded != null) { buf.append(decoded); i = semi + 1 } else { buf.append('&'); i++ }
            }

            // base reading if alt text/furigana (bracketed)
            c == '[' -> {
                val close = markup.indexOf(']', i + 1)
                val base = if (close == -1) "" else buf.takeLastWhile { !it.isWhitespace() }.toString()
                if (close == -1 || base.isEmpty()) {
                    buf.append('['); i++
                } else {
                    buf.deleteRange(buf.length - base.length, buf.length)
                    flush()
                    out.add(RichNode.Ruby(base, markup.substring(i + 1, close), spans(), color()))
                    i = close + 1
                }
            }

            // tags
            c == '<' -> {
                val gt = markup.indexOf('>', i)
                if (gt == -1) { buf.append('<'); i++; continue }
                val body = markup.substring(i + 1, gt).trim()
                var after = gt + 1
                val closing = body.startsWith("/")
                val name = body.removePrefix("/").trimStart().takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                when (name) {
                    "br" -> { flush(); out.add(RichNode.Break) }
                    "b", "strong" -> { flush(); if (closing) bold = (bold - 1).coerceAtLeast(0) else bold++ }
                    "i", "em" -> { flush(); if (closing) italic = (italic - 1).coerceAtLeast(0) else italic++ }
                    "u" -> { flush(); if (closing) underline = (underline - 1).coerceAtLeast(0) else underline++ }
                    "span", "font" -> {
                        flush()
                        if (closing) { if (colorStack.isNotEmpty()) colorStack.removeLast() }
                        else colorStack.addLast(extractColor(body))
                    }
                    "ruby" -> if (!closing) {
                        flush()
                        val end = markup.indexOf("</ruby>", after, ignoreCase = true)
                        val inner = markup.substring(after, if (end == -1) n else end)
                        parseRubyBlock(inner, spans(), color(), out)
                        after = if (end == -1) n else end + "</ruby>".length
                    }
                    else -> {}
                }
                i = after
            }

            else -> { buf.append(c); i++ }
        }
    }
    flush()
    return RichDoc(out)
}

private fun parseRubyBlock(innerRaw: String, spans: Set<RichSpan>, color: Long?, out: MutableList<RichNode>) {
    val inner = removeRpFallbacks(innerRaw)
    val base = StringBuilder()
    var i = 0
    while (i < inner.length) {
        val rt = inner.indexOf("<rt>", i, ignoreCase = true)
        if (rt == -1) { base.append(decodeEntities(stripTags(inner.substring(i)))); break }
        base.append(decodeEntities(stripTags(inner.substring(i, rt))))
        val rtEnd = inner.indexOf("</rt>", rt + 4, ignoreCase = true)
        val reading = decodeEntities(stripTags(inner.substring(rt + 4, if (rtEnd == -1) inner.length else rtEnd)))
        val b = base.toString(); base.clear()
        if (b.isNotEmpty() || reading.isNotEmpty()) out.add(RichNode.Ruby(b, reading, spans, color))
        i = if (rtEnd == -1) inner.length else rtEnd + "</rt>".length
    }
    val trailing = base.toString()
    if (trailing.isNotEmpty()) out.add(RichNode.Run(trailing, spans, color))
}

internal fun richTextToPlain(markup: String): String =
    parseRich(markup).nodes.joinToString("") { node ->
        when (node) {
            is RichNode.Run -> node.text
            is RichNode.Ruby -> node.base
            RichNode.Break -> " "
        }
    }.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

// make alt text/furigana more searchable
internal fun richTextToSearchText(markup: String): String {
    val doc = parseRich(markup)
    fun render(useReadings: Boolean): String = doc.nodes.joinToString("") { node ->
        when (node) {
            is RichNode.Run -> node.text
            is RichNode.Ruby -> if (useReadings) node.reading else node.base
            RichNode.Break -> " "
        }
    }.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    val base = render(false)
    return if (doc.nodes.any { it is RichNode.Ruby }) base + "\n" + render(true) else base
}


private fun stripTags(s: String): String {
    if ('<' !in s) return s
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '<') {
            val gt = s.indexOf('>', i)
            if (gt == -1) { sb.append(s.substring(i)); break }
            i = gt + 1
        } else { sb.append(s[i]); i++ }
    }
    return sb.toString()
}

private fun removeRpFallbacks(s: String): String {
    if (s.indexOf("<rp>", ignoreCase = true) == -1) return s
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val open = s.indexOf("<rp>", i, ignoreCase = true)
        if (open == -1) { sb.append(s.substring(i)); break }
        sb.append(s.substring(i, open))
        val close = s.indexOf("</rp>", open, ignoreCase = true)
        i = if (close == -1) s.length else close + "</rp>".length
    }
    return sb.toString()
}

private fun decodeEntities(s: String): String {
    if ('&' !in s) return s
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '&') {
            val semi = s.indexOf(';', i)
            val decoded = if (semi != -1 && semi - i <= 7) decodeEntity(s.substring(i, semi + 1)) else null
            if (decoded != null) { sb.append(decoded); i = semi + 1; continue }
        }
        sb.append(s[i]); i++
    }
    return sb.toString()
}

private fun decodeEntity(entity: String): String? = when (entity.lowercase()) {
    "&lt;" -> "<"
    "&gt;" -> ">"
    "&amp;" -> "&"
    "&quot;" -> "\""
    "&apos;", "&#39;" -> "'"
    "&nbsp;" -> " "
    else -> null
}

private fun extractColor(tagBody: String): Long? {
    val ci = tagBody.indexOf("color", ignoreCase = true)
    if (ci == -1) return null
    var k = ci + "color".length
    while (k < tagBody.length && tagBody[k] in ": ='\"") k++
    val start = k
    while (k < tagBody.length && tagBody[k] != ';' && tagBody[k] != '"' && tagBody[k] != '\'') k++
    return parseCssColor(tagBody.substring(start, k).trim())
}

private fun parseCssColor(value: String): Long? = runCatching {
    when {
        value.startsWith("#") -> {
            val hex = value.drop(1)
            val rgb = when (hex.length) {
                3 -> hex.map { "$it$it" }.joinToString("")
                6 -> hex
                else -> return null
            }
            0xFF000000L or rgb.substring(0, 6).toLong(16)
        }
        value.startsWith("rgb", ignoreCase = true) -> {
            val parts = value.substringAfter('(').substringBefore(')').split(',').map { it.trim().toInt() }
            if (parts.size < 3) return null
            0xFF000000L or (parts[0].toLong() shl 16) or (parts[1].toLong() shl 8) or parts[2].toLong()
        }
        else -> null
    }
}.getOrNull()
