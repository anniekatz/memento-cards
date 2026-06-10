package com.annie.memento.data.ankiImporter

// converts anki note (html) into memento rich-text
data class ConvertedField(
    val markup: String,
    val plain: String,
    val soundRefs: List<String>,
    val imageRefs: List<String>,
    val isRich: Boolean,
)

fun convertAnkiField(html: String): ConvertedField {
    val sounds = ArrayList<String>()
    var text = stripCloze(html)
    text = SOUND_REGEX.replace(text) { match ->
        decodeHtmlEntities(match.groupValues[1]).trim().takeIf { it.isNotEmpty() }?.let { sounds.add(it) }
        ""
    }
    return FieldWalker(text, sounds).convert()
}

private class FieldWalker(private val input: String, private val sounds: List<String>) {
    private val markup = StringBuilder()
    private val plain = StringBuilder()
    private val textBuf = StringBuilder()
    private val images = ArrayList<String>()
    private val spanStack = ArrayDeque<Boolean>()
    private var isRich = false
    private var contentEmitted = false
    private var pendingBr = 0
    private var pendingBlock = false
    private var rpDepth = 0
    private var rubyDepth = 0

    fun convert(): ConvertedField {
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                c == '<' -> i = handleTag(i)
                c == '&' -> i = handleEntity(i)
                else -> {
                    if (rpDepth == 0) textBuf.append(c)
                    i++
                }
            }
        }
        flushText()
        return ConvertedField(
            markup = markup.toString(),
            plain = plain.toString(),
            soundRefs = sounds,
            imageRefs = images,
            isRich = isRich,
        )
    }

    private fun handleEntity(start: Int): Int {
        val semi = input.indexOf(';', start)
        if (semi != -1 && semi - start in 2..11) {
            val decoded = decodeEntity(input.substring(start, semi + 1))
            if (decoded != null) {
                if (rpDepth == 0) textBuf.append(decoded)
                return semi + 1
            }
        }
        if (rpDepth == 0) textBuf.append('&')
        return start + 1
    }

    private fun handleTag(start: Int): Int {
        if (input.startsWith("<!--", start)) {
            val end = input.indexOf("-->", start)
            return if (end == -1) input.length else end + 3
        }
        if (input.startsWith("<!", start)) {
            val end = input.indexOf('>', start)
            return if (end == -1) input.length else end + 1
        }
        val gt = input.indexOf('>', start)
        if (gt == -1) {
            textBuf.append('<')
            return start + 1
        }
        val body = input.substring(start + 1, gt).trim().removeSuffix("/").trim()
        val closing = body.startsWith("/")
        val nameAndAttrs = body.removePrefix("/").trimStart()
        val name = nameAndAttrs.takeWhile { !it.isWhitespace() }.lowercase()
        val attrs = nameAndAttrs.drop(name.length)
        var next = gt + 1

        when (name) {
            "b", "strong" -> emitFormat(if (closing) "</b>" else "<b>")
            "i", "em" -> emitFormat(if (closing) "</i>" else "<i>")
            "u", "ins" -> emitFormat(if (closing) "</u>" else "<u>")
            "br" -> {
                flushText()
                pendingBr++
            }
            "div", "p", "li", "ul", "ol", "tr", "table", "blockquote", "section", "article",
            "h1", "h2", "h3", "h4", "h5", "h6", "hr", "dd", "dt",
            -> {
                flushText()
                pendingBlock = true
            }
            "span", "font" -> {
                if (closing) {
                    if (spanStack.removeLastOrNull() == true) emitFormat("</span>")
                } else {
                    val color = extractTagColor(name, attrs)
                    if (color != null) {
                        emitFormat("<span style=\"color:$color\">")
                        spanStack.addLast(true)
                    } else {
                        spanStack.addLast(false)
                    }
                }
            }
            "ruby" -> {
                emitFormat(if (closing) "</ruby>" else "<ruby>")
                rubyDepth = if (closing) (rubyDepth - 1).coerceAtLeast(0) else rubyDepth + 1
            }
            "rt" -> {
                if (closing) {
                    emitRaw("</rt>", plainText = "]")
                } else {
                    emitRaw("<rt>", plainText = "[")
                }
            }
            "rp" -> rpDepth = if (closing) (rpDepth - 1).coerceAtLeast(0) else rpDepth + 1
            "img" -> {
                val src = extractAttr(attrs, "src")
                if (!src.isNullOrBlank() && !src.startsWith("data:")) {
                    images.add(decodeHtmlEntities(src).trim())
                }
            }
            "style", "script", "head", "title" -> if (!closing) {
                val close = input.indexOf("</$name", gt + 1, ignoreCase = true)
                next = if (close == -1) input.length else {
                    val closeGt = input.indexOf('>', close)
                    if (closeGt == -1) input.length else closeGt + 1
                }
            }
            else -> {} // unknown tags unwrap: keep their text, drop the tag
        }
        return next
    }

    private fun emitFormat(tag: String) {
        flushText()
        emitPendingBreaks()
        markup.append(tag)
        isRich = true
    }

    private fun emitRaw(markupText: String, plainText: String) {
        flushText()
        emitPendingBreaks()
        markup.append(markupText)
        plain.append(plainText)
        isRich = true
    }

    private fun emitPendingBreaks() {
        val breaks = when {
            pendingBr > 0 -> minOf(pendingBr, 2)
            pendingBlock -> 1
            else -> 0
        }
        if (breaks > 0 && contentEmitted) {
            repeat(breaks) {
                markup.append("<br>")
                plain.append('\n')
            }
        }
        pendingBr = 0
        pendingBlock = false
    }

    private fun flushText() {
        if (textBuf.isEmpty()) return
        val text = textBuf.toString()
        textBuf.clear()
        emitPendingBreaks()
        appendTextWithFurigana(text)
        contentEmitted = true
    }

    private fun appendTextWithFurigana(text: String) {
        if (rubyDepth > 0) {
            // strips tags and decodes  entities but not backslash escapes (entity escape instead)
            markup.append(entityEscape(text))
            plain.append(text)
            return
        }
        var last = 0
        for (match in FURIGANA_REGEX.findAll(text)) {
            val base = match.groupValues[2]
            val reading = match.groupValues[3]
            if (reading.isBlank()) continue
            appendEscaped(text.substring(last, match.range.first))
            markup.append("<ruby>").append(escapeMarkup(base)).append("<rt>")
                .append(escapeMarkup(reading)).append("</rt></ruby>")
            plain.append(base).append('[').append(reading).append(']')
            isRich = true
            last = match.range.last + 1
        }
        appendEscaped(text.substring(last))
    }

    private fun appendEscaped(text: String) {
        if (text.isEmpty()) return
        markup.append(escapeMarkup(text))
        plain.append(text)
    }
}

private fun entityEscape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun escapeMarkup(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
        if (c in "\\[]<>&") sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

// {{c1::answer}} / {{c1::answer::hint}} -> answer
private val CLOZE_REGEX = Regex("""\{\{c\d+::((?:(?!\{\{c\d+::)(?!\}\})[\s\S])*?)\}\}""")

internal fun stripCloze(text: String): String {
    var current = text
    repeat(10) {
        val next = CLOZE_REGEX.replace(current) { it.groupValues[1].substringBefore("::") }
        if (next == current) return next
        current = next
    }
    return current
}

private val SOUND_REGEX = Regex("""\[sound:([^\[\]]*)\]""")

private val FURIGANA_REGEX = Regex(
    """([ 　]?)([㐀-䶿一-鿿々〆ヵヶ]+)\[([぀-ヿ･-ﾟ　 ]+)\]""",
)

private fun extractAttr(attrs: String, attr: String): String? {
    val regex = Regex("""\b$attr\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
    val match = regex.find(attrs) ?: return null
    return match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }
}

private fun extractTagColor(tagName: String, attrs: String): String? {
    if (tagName == "font") {
        extractAttr(attrs, "color")?.let { return parseCssColorValue(it) }
    }
    val style = extractAttr(attrs, "style") ?: return null
    for (declaration in decodeHtmlEntities(style).split(';')) {
        val parts = declaration.split(':', limit = 2)
        if (parts.size == 2 && parts[0].trim().equals("color", ignoreCase = true)) {
            return parseCssColorValue(parts[1].trim())
        }
    }
    return null
}

internal fun parseCssColorValue(value: String): String? {
    val v = value.trim().removeSuffix("!important").trim()
    return when {
        v.startsWith("#") -> {
            val hex = v.drop(1)
            when (hex.length) {
                3 -> if (hex.all { it.isHexDigit() }) "#" + hex.map { "$it$it" }.joinToString("") else null
                6 -> if (hex.all { it.isHexDigit() }) "#$hex" else null
                8 -> if (hex.all { it.isHexDigit() }) "#${hex.take(6)}" else null
                else -> null
            }?.lowercase()
        }
        v.startsWith("rgb", ignoreCase = true) -> {
            val parts = v.substringAfter('(', "").substringBefore(')').split(',', '/', ' ')
                .filter { it.isNotBlank() }
                .mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size < 3) return null
            val (r, g, b) = parts.map { it.toInt().coerceIn(0, 255) }
            "#" + listOf(r, g, b).joinToString("") { it.toString(16).padStart(2, '0') }
        }
        else -> NAMED_CSS_COLORS[v.lowercase()]
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

internal fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        if (text[i] == '&') {
            val semi = text.indexOf(';', i)
            if (semi != -1 && semi - i in 2..11) {
                val decoded = decodeEntity(text.substring(i, semi + 1))
                if (decoded != null) {
                    sb.append(decoded)
                    i = semi + 1
                    continue
                }
            }
        }
        sb.append(text[i])
        i++
    }
    return sb.toString()
}

private fun decodeEntity(entity: String): String? {
    val body = entity.removePrefix("&").removeSuffix(";")
    if (body.startsWith("#")) {
        val code = body.drop(1)
        val cp = if (code.startsWith("x") || code.startsWith("X")) {
            code.drop(1).toIntOrNull(16)
        } else {
            code.toIntOrNull()
        } ?: return null
        return codePointToString(cp)
    }
    return NAMED_ENTITIES[body]
}

private fun codePointToString(cp: Int): String? = when (cp) {
    in 0x20..0xD7FF, in 0xE000..0xFFFD -> cp.toChar().toString()
    in 0x10000..0x10FFFF -> {
        val c = cp - 0x10000
        charArrayOf(
            (0xD800 + (c ushr 10)).toChar(),
            (0xDC00 + (c and 0x3FF)).toChar(),
        ).concatToString()
    }
    0x9, 0xA, 0xD -> cp.toChar().toString()
    else -> null
}

private val NAMED_ENTITIES: Map<String, String> = mapOf(
    "lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "shy" to "", "zwnj" to "", "zwj" to "",
    "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "minus" to "−",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    "laquo" to "«", "raquo" to "»", "middot" to "·", "bull" to "•",
    "times" to "×", "divide" to "÷", "plusmn" to "±", "deg" to "°",
    "copy" to "©", "reg" to "®", "trade" to "™", "sect" to "§", "para" to "¶",
    "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
    "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "sup2" to "²", "sup3" to "³",
    "micro" to "µ", "dagger" to "†", "Dagger" to "‡", "permil" to "‰",
    "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓", "harr" to "↔",
)

internal val NAMED_CSS_COLORS: Map<String, String> = mapOf(
    "aliceblue" to "#f0f8ff", "antiquewhite" to "#faebd7", "aqua" to "#00ffff",
    "aquamarine" to "#7fffd4", "azure" to "#f0ffff", "beige" to "#f5f5dc",
    "bisque" to "#ffe4c4", "black" to "#000000", "blanchedalmond" to "#ffebcd",
    "blue" to "#0000ff", "blueviolet" to "#8a2be2", "brown" to "#a52a2a",
    "burlywood" to "#deb887", "cadetblue" to "#5f9ea0", "chartreuse" to "#7fff00",
    "chocolate" to "#d2691e", "coral" to "#ff7f50", "cornflowerblue" to "#6495ed",
    "cornsilk" to "#fff8dc", "crimson" to "#dc143c", "cyan" to "#00ffff",
    "darkblue" to "#00008b", "darkcyan" to "#008b8b", "darkgoldenrod" to "#b8860b",
    "darkgray" to "#a9a9a9", "darkgreen" to "#006400", "darkgrey" to "#a9a9a9",
    "darkkhaki" to "#bdb76b", "darkmagenta" to "#8b008b", "darkolivegreen" to "#556b2f",
    "darkorange" to "#ff8c00", "darkorchid" to "#9932cc", "darkred" to "#8b0000",
    "darksalmon" to "#e9967a", "darkseagreen" to "#8fbc8f", "darkslateblue" to "#483d8b",
    "darkslategray" to "#2f4f4f", "darkslategrey" to "#2f4f4f", "darkturquoise" to "#00ced1",
    "darkviolet" to "#9400d3", "deeppink" to "#ff1493", "deepskyblue" to "#00bfff",
    "dimgray" to "#696969", "dimgrey" to "#696969", "dodgerblue" to "#1e90ff",
    "firebrick" to "#b22222", "floralwhite" to "#fffaf0", "forestgreen" to "#228b22",
    "fuchsia" to "#ff00ff", "gainsboro" to "#dcdcdc", "ghostwhite" to "#f8f8ff",
    "gold" to "#ffd700", "goldenrod" to "#daa520", "gray" to "#808080",
    "green" to "#008000", "greenyellow" to "#adff2f", "grey" to "#808080",
    "honeydew" to "#f0fff0", "hotpink" to "#ff69b4", "indianred" to "#cd5c5c",
    "indigo" to "#4b0082", "ivory" to "#fffff0", "khaki" to "#f0e68c",
    "lavender" to "#e6e6fa", "lavenderblush" to "#fff0f5", "lawngreen" to "#7cfc00",
    "lemonchiffon" to "#fffacd", "lightblue" to "#add8e6", "lightcoral" to "#f08080",
    "lightcyan" to "#e0ffff", "lightgoldenrodyellow" to "#fafad2", "lightgray" to "#d3d3d3",
    "lightgreen" to "#90ee90", "lightgrey" to "#d3d3d3", "lightpink" to "#ffb6c1",
    "lightsalmon" to "#ffa07a", "lightseagreen" to "#20b2aa", "lightskyblue" to "#87cefa",
    "lightslategray" to "#778899", "lightslategrey" to "#778899", "lightsteelblue" to "#b0c4de",
    "lightyellow" to "#ffffe0", "lime" to "#00ff00", "limegreen" to "#32cd32",
    "linen" to "#faf0e6", "magenta" to "#ff00ff", "maroon" to "#800000",
    "mediumaquamarine" to "#66cdaa", "mediumblue" to "#0000cd", "mediumorchid" to "#ba55d3",
    "mediumpurple" to "#9370db", "mediumseagreen" to "#3cb371", "mediumslateblue" to "#7b68ee",
    "mediumspringgreen" to "#00fa9a", "mediumturquoise" to "#48d1cc", "mediumvioletred" to "#c71585",
    "midnightblue" to "#191970", "mintcream" to "#f5fffa", "mistyrose" to "#ffe4e1",
    "moccasin" to "#ffe4b5", "navajowhite" to "#ffdead", "navy" to "#000080",
    "oldlace" to "#fdf5e6", "olive" to "#808000", "olivedrab" to "#6b8e23",
    "orange" to "#ffa500", "orangered" to "#ff4500", "orchid" to "#da70d6",
    "palegoldenrod" to "#eee8aa", "palegreen" to "#98fb98", "paleturquoise" to "#afeeee",
    "palevioletred" to "#db7093", "papayawhip" to "#ffefd5", "peachpuff" to "#ffdab9",
    "peru" to "#cd853f", "pink" to "#ffc0cb", "plum" to "#dda0dd",
    "powderblue" to "#b0e0e6", "purple" to "#800080", "rebeccapurple" to "#663399",
    "red" to "#ff0000", "rosybrown" to "#bc8f8f", "royalblue" to "#4169e1",
    "saddlebrown" to "#8b4513", "salmon" to "#fa8072", "sandybrown" to "#f4a460",
    "seagreen" to "#2e8b57", "seashell" to "#fff5ee", "sienna" to "#a0522d",
    "silver" to "#c0c0c0", "skyblue" to "#87ceeb", "slateblue" to "#6a5acd",
    "slategray" to "#708090", "slategrey" to "#708090", "snow" to "#fffafa",
    "springgreen" to "#00ff7f", "steelblue" to "#4682b4", "tan" to "#d2b48c",
    "teal" to "#008080", "thistle" to "#d8bfd8", "tomato" to "#ff6347",
    "turquoise" to "#40e0d0", "violet" to "#ee82ee", "wheat" to "#f5deb3",
    "white" to "#ffffff", "whitesmoke" to "#f5f5f5", "yellow" to "#ffff00",
    "yellowgreen" to "#9acd32",
)
