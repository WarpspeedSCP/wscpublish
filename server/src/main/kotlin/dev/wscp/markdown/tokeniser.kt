package dev.wscp.markdown

import dev.wscp.data.ArticleFrontmatter
import dev.wscp.utils.pop
import dev.wscp.utils.push
import kotlinx.serialization.decodeFromString


data class LineColPosition(val startLine: Int, val startCol: Int, val endLine: Int, val endCol: Int) {
    override fun toString(): String = "$startLine:$startCol-$endLine:$endCol"
}

class LineColTracker(val text: String) {

    val lineColCache: List<Pair<IntRange, Pair<Int, Int>>> = let {
        var lineIdx = 0
        var nCols = 0
        val data = mutableListOf<Pair<IntRange, Pair<Int, Int>>>()
        for ((idx, i) in text.withIndex()) {
            if (i == '\n') {
                data.push(Pair(idx..(idx + nCols + 1), lineIdx to (nCols + 1)))
                lineIdx += 1
                nCols = 0
            } else {
                nCols += 1
            }
        }
        data
    }

    fun getLineColForSpan(span: IntRange): LineColPosition? {
        val startLineEntry = lineColCache.find { it.first.contains(span.first) }
        val endLineEntry = lineColCache.find { it.first.contains(span.last) }

        if (startLineEntry == null) {
            return null
        }

        val startLine = startLineEntry.second.first
        val startCol = span.first - startLineEntry.first.first

        if (endLineEntry == null) {
            val lastEntry = lineColCache.last()
            val (endLine, endCol) = lastEntry.second
            return LineColPosition(startLine, startCol, endLine, endCol)
        } else if (endLineEntry == startLineEntry) {
            return LineColPosition(startLine, startCol, startLine, startCol)
        } else if (endLineEntry.second.first == startLine) {
            val endCol = startCol + span.last - span.first
            return LineColPosition(startLine, startCol, startLine, endCol)
        } else {
            val endLine = endLineEntry.second.first
            val endCol = span.last - endLineEntry.first.first
            return LineColPosition(startLine, startCol, endLine, endCol)
        }
    }
}

sealed class MDToken(open val span: IntRange) {

    sealed interface MDSymbolToken {
        fun stringRepr(): String
    }

    // ([^\n \t]`)|(`[^\n \t])
    data class SINGLE_GRAVE(override val span: IntRange, val language: String? = null) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "`"
    }

    // (\n```)|(```\n)
    data class TRIPLE_GRAVE(override val span: IntRange, val language: String? = null) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "```"
    }

    // ([^\n \t]*)|(*[^\n \t])
    data class SINGLE_ASTERISK(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "*"
    }

    // ([^\n \t]**)|(**[^\n \t])
    data class DOUBLE_ASTERISK(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "**"
    }

    // ([^\n \t]***)|(***[^\n \t])
    data class TRIPLE_ASTERISK(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "***"
    }

    // _
    data class SINGLE_UNDERSCORE(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "_"
    }

    sealed class LIST_ITEM(open val level: Int, override val span: IntRange) : MDToken(span)
    data class UL_ITEM(override val level: Int, override val span: IntRange) : LIST_ITEM(level, span), MDSymbolToken {
        override fun stringRepr(): String = "* "
    }
    data class OL_ITEM(override val level: Int, override val span: IntRange) : LIST_ITEM(level, span), MDSymbolToken {
        override fun stringRepr(): String = "1. "
    }
    // Uses the same level structure.
    data class BQUOTE(override val level: Int, override val span: IntRange) : LIST_ITEM(level, span), MDSymbolToken {
        override fun stringRepr(): String = "> "
    }

    // ---
    data class TRIPLE_HYPHEN(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "---"
    }

    // ===
    data class TRIPLE_EQUALS(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "==="
    }

    data class TRIPLE_UNDERSCORE(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "___"
    }

    data class LINEBREAK(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "\n"
    }

    // ^<[^>]*>$
    data class HTML_TAG(val tag: String, val attributes: Map<String, String?>, override val span: IntRange, val selfClosing: Boolean = false) : MDToken(span) {
    }
    data class HTML_CLOSE_TAG(val tag: String, override val span: IntRange) : MDToken(span) {
    }
    data class SCRIPT_TAG(val script: String, val attributes: Map<String, String?>, override val span: IntRange) : MDToken(span) {
    }

    data class TEXT(val text: String, override val span: IntRange) : MDToken(span) {
        operator fun plus(other: String) = TEXT(
            this.text + other,
            this.span.first..<(this.span.last + other.length)
        )
    }

    // ^![.*$
    data class IMAGE_START(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "!["
    }

    // ^[.*$
    data class LINK_START(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "["
    }

    data class FOOTNOTE_REF(override val span: IntRange, val ref: String) : MDToken(span)

    data class FOOTNOTE(override val span: IntRange, val ref: String) : MDToken(span)

    // ^](.*$
    data class LINK_INTERSTICE(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = "]("
    }

    data class LINK_URI(val uri: String, override val span: IntRange) : MDToken(span)

    // ^).*$
    data class LINK_END(override val span: IntRange) : MDToken(span), MDSymbolToken {
        override fun stringRepr(): String = ")"
    }

    sealed class HTag(override val span: IntRange) : MDToken(span), MDSymbolToken {
        data class H1(override val span: IntRange) : HTag(span) {
            override fun stringRepr(): String = "# "
        }

        data class H2(override val span: IntRange) : HTag(span) {
            override fun stringRepr(): String = "## "
        }

        data class H3(override val span: IntRange) : HTag(span) {
            override fun stringRepr(): String = "### "
        }

        data class H4(override val span: IntRange) : HTag(span) {
            override fun stringRepr(): String = "#### "
        }

        data class H5(override val span: IntRange) : HTag(span) {
            override fun stringRepr(): String = "##### "
        }

        data class H6(override val span: IntRange) : HTag(span) {
            override fun stringRepr(): String = "###### "
        }
    }

    data class NEWLINE(override val span: IntRange) : MDToken(span)

    data class ESCAPE(override val span: IntRange, val escapedChar: Char) : MDToken(span)

    data class EOF(override val span: IntRange) : MDToken(span)
}

sealed interface MDTokenHint {
    object IsInlineBreak : MDTokenHint
    object IsRefLink : MDTokenHint
    object IsRef : MDTokenHint
    object IsLinkStart : MDTokenHint
    object IsLinkEnd : MDTokenHint
    class IsBQuote(val level: Int) : MDTokenHint
    class IsUListStart(val indent: Int) : MDTokenHint
    class IsOListStart(val indent: Int) : MDTokenHint
}

// TODO: Use java vector API to speed this up.
class MDTokeniser(private val input: String) {
    private val tokens = mutableListOf<MDToken>()
    private var currToken = ""
    private fun updateTokens(span: IntRange, tokenHint: MDTokenHint? = null) = if (currToken.isNotEmpty()) {
        tokens.add(
            when {
                currToken.startsWith('\\') -> {
                    if (tokenHint is MDTokenHint.IsInlineBreak) MDToken.LINEBREAK(span)
                    else MDToken.ESCAPE(span, currToken[1])
                }
                currToken == "\n" -> MDToken.NEWLINE(span)
                currToken == "`" -> MDToken.SINGLE_GRAVE(span)
                currToken.startsWith("```") -> {
                    if (currToken.length > 3) {
                        MDToken.TRIPLE_GRAVE(span, currToken.substring(3))
                    } else {
                        MDToken.TRIPLE_GRAVE(span)
                    }
                }

                currToken == "*" -> if (tokenHint is MDTokenHint.IsUListStart) MDToken.UL_ITEM(
                    tokenHint.indent,
                    span
                ) else MDToken.SINGLE_ASTERISK(span)
                currToken.startsWith('>') && tokenHint is MDTokenHint.IsBQuote -> MDToken.BQUOTE(tokenHint.level, span)
                currToken == "**" -> MDToken.DOUBLE_ASTERISK(span)
                currToken == "***" -> MDToken.TRIPLE_ASTERISK(span)
                currToken == "1." && tokenHint is MDTokenHint.IsOListStart -> MDToken.OL_ITEM(tokenHint.indent, span)
                currToken == "-" && tokenHint is MDTokenHint.IsUListStart -> MDToken.UL_ITEM(tokenHint.indent, span)
                currToken == "![" -> MDToken.IMAGE_START(span)
                currToken.startsWith("[^") -> {
                    if (tokenHint == MDTokenHint.IsRefLink) {
                        MDToken.FOOTNOTE_REF(span, currToken.removePrefix("[^").removeSuffix("]"))
                    } else {
                        MDToken.FOOTNOTE(span, currToken.removePrefix("[^").removeSuffix("]:"))
                    }
                }
                currToken == "[" && tokenHint == MDTokenHint.IsLinkStart -> MDToken.LINK_START(span)
                currToken == "](" -> MDToken.LINK_INTERSTICE(span)
                currToken == ")" && tokenHint == MDTokenHint.IsLinkEnd -> MDToken.LINK_END(span)
                currToken.startsWith("---") && currToken.all { it == '-' || it.isWhitespace() } -> MDToken.TRIPLE_HYPHEN(span)
                currToken.startsWith("===") && currToken.all { it == '=' || it.isWhitespace() } -> MDToken.TRIPLE_EQUALS(span)
                currToken.startsWith("___") && currToken.all { it == '_' || it.isWhitespace() } -> MDToken.TRIPLE_EQUALS(span)
                currToken == "#" -> MDToken.HTag.H1(span)
                currToken == "##" -> MDToken.HTag.H2(span)
                currToken == "###" -> MDToken.HTag.H2(span)
                currToken == "####" -> MDToken.HTag.H4(span)
                currToken == "#####" -> MDToken.HTag.H5(span)
                currToken == "######" -> MDToken.HTag.H6(span)
                else -> {
                    val last = tokens.lastOrNull()
                    if (last is MDToken.TEXT) {
                        tokens.pop() as MDToken.TEXT + currToken
                    } else MDToken.TEXT(currToken, span)
                }
            }
        )
        currToken = ""
    } else Unit

    private fun tokenise(): List<MDToken> {
        var index = 0

        val mdInput = if (input.startsWith("+++")) {
            var tmpIndex = 3
            val finalIndex = input.indexOf("\n+++\n", startIndex = tmpIndex)
            val frontmatter = input.substring(tmpIndex, finalIndex)

            val parsedFrontmatter = com.akuleshov7.ktoml.Toml.decodeFromString<ArticleFrontmatter>(frontmatter)

            val rest = input.substring(finalIndex + 5)

            rest
        } else input

        outer@while (index < mdInput.length) {
            val chr = mdInput[index]
            when {
                chr == '>' -> {
                    val tokensTillPrevNewline = tokens.asReversed().takeWhile { it !is MDToken.NEWLINE }
                    var tmpIndex = index
                    var tempChr: Char? = mdInput[tmpIndex]
                    while (tempChr == '>') {
                        currToken += tempChr
                        tmpIndex = tmpIndex + 1
                        tempChr = mdInput.getOrNull(tmpIndex)
                    }
                    val nextIsWhitespace = mdInput.getOrNull(tmpIndex).let { it != null && it != '\n' && it.isWhitespace() }

                    if (tokensTillPrevNewline.all { it is MDToken.NEWLINE || it is MDToken.UL_ITEM || it is MDToken.OL_ITEM || (it is MDToken.TEXT && it.text.isBlank()) } && nextIsWhitespace) {
                        updateTokens(index..<(tmpIndex + 1), MDTokenHint.IsBQuote(currToken.length))
                    } else {
                        updateTokens(index..<(tmpIndex + 1))
                    }
                    index = tmpIndex
                }
                chr == '<' -> {
                    val attributes = mutableListOf<Pair<String, String?>>()
                    var isClose = false
                    var isSelfClosing = false
                    var isScript = false
                    var tmpIndex = index
                    var tagName = ""
                    // <abc def="a" fgh="e" />
                    while (tmpIndex < mdInput.length && mdInput[tmpIndex] != '>') {
                        if (isClose) {
                            tmpIndex += 1
                            continue
                        }

                        var currThing = mdInput[tmpIndex]

                        while (currThing.isWhitespace()) {
                            tmpIndex += 1
                            currThing = mdInput[tmpIndex]
                        }

                        if (currThing == '<') {
                            if (mdInput.getOrNull(tmpIndex + 1) == '/'){
                                isClose = true
                                tmpIndex += 1
                            }
                            tmpIndex += 1
                            tagName = mdInput.substring(tmpIndex).takeWhile { it.isWordPart() }
                            tmpIndex += tagName.length

                            if (tagName == "script") isScript = true

                            continue
                        }

                        if (currThing == '/' && mdInput.getOrNull(tmpIndex + 1) == '>') {
                            isSelfClosing = true
                            tmpIndex += 1
                            break
                        }

                        val attributeName = if (currThing.isLetter()) {
                            val savedIndex = tmpIndex
                            while (tmpIndex < mdInput.length && mdInput[tmpIndex].isWordPart()) tmpIndex += 1
                            currThing = mdInput.getOrNull(tmpIndex) ?: throw IllegalStateException("Unexpected end of input.")
                            mdInput.substring(savedIndex, tmpIndex)
                        } else null

                        if (currThing == '=') {
                            tmpIndex += 1
                            currThing = mdInput[tmpIndex]
                        }

                        val attributeValue = if (currThing == '"') {
                            val savedIndex = tmpIndex
                            tmpIndex += 1
                            while (tmpIndex < mdInput.length && mdInput[tmpIndex] != '"') tmpIndex += 1
                            val attributeContents = mdInput.substring(savedIndex, tmpIndex)
                            attributeContents.trim('"')
                        } else null

                        if (attributeName != null) {
                            attributes.push(attributeName to attributeValue)
                        }

                        if (tmpIndex >= mdInput.length) throw IllegalStateException("Invalid HTML tag at byte $index!")

                        if (mdInput[tmpIndex] == '>') {
                            if (isScript) {
                                val endIndex = mdInput.indexOf("</script>", startIndex = tmpIndex)
                                if (endIndex < 0) throw IllegalStateException("Unclosed script tag at byte $index!")
                                val actualEnd = endIndex + "</script>".length
                                val contents = mdInput.substring(tmpIndex + 1, endIndex)
                                tokens += MDToken.SCRIPT_TAG(script = contents, attributes.toMap(), index ..< actualEnd)
                                tmpIndex = actualEnd
                                isSelfClosing = true
                                break
                            }
                        }

                        tmpIndex += 1
                    }

                    if (tmpIndex == input.length) {
                        currToken += chr
                        updateTokens(index..<(index + 1))
                        index += 1
                        continue
                    }

                    if (isSelfClosing) {
                        if (!isScript) {
                            val token = MDToken.HTML_TAG(tagName, attributes.toMap(), index ..< tmpIndex, selfClosing = true)
                            tokens += token
                        }
                    } else {
                        val token = if (isClose) {
                            MDToken.HTML_CLOSE_TAG(tagName, index..< tmpIndex)
                        } else {
                            MDToken.HTML_TAG(tagName, attributes.toMap(), index ..< tmpIndex)
                        }
                        tokens += token
                    }

                    index = tmpIndex
                }
                chr == '\\' -> {
                    updateTokens((index - currToken.length)..<index)
                    currToken += chr
                    currToken += mdInput.getOrNull(index + 1) ?: ""
                    if (currToken.last() == '\n') {
                        updateTokens(index..(index + 2), tokenHint = MDTokenHint.IsInlineBreak)
                    } else {
                        updateTokens(index..(index + 2), tokenHint = MDTokenHint.IsLinkStart)
                    }
                    index += 2
                    continue
                }

                chr == '-' -> {
//                    updateTokens((index - currToken.length)..index)
                    if (mdInput.getOrNull(index + 1)?.isWhitespace() == true) {
                        val optionalTypeHint = getListTypeHint(isOrdered = false)
                        currToken += "-"

                        updateTokens(index..(index + 2), tokenHint = optionalTypeHint)
                    }
                }

                chr == '1' -> {
//                    updateTokens((index - currToken.length)..index)
                    if (mdInput.getOrNull(index + 1) == '.' && mdInput.getOrNull(index + 2)?.isWhitespace() == true) {
                        val optionalTypeHint = getListTypeHint(true)
                        currToken += "1."

                        updateTokens(index..(index + 2), tokenHint = optionalTypeHint)
                        index += 2
                        continue
                    }
                }

                chr == '*' || chr == '~' || chr == '#' || chr == '_' || chr == '`' -> {
//                    updateTokens((index - currToken.length)..index)
                    val optionalTypeHint = getListTypeHint(false)

                    var tempIdx = index
                    while (mdInput.getOrNull(tempIdx) == chr) {
                        if (tempIdx - index > 3) {
                            break
                        } else {
                            currToken += chr
                            tempIdx += 1
                        }
                    }

                    updateTokens(
                        (tempIdx - currToken.length)..tempIdx,
                        if (mdInput.getOrNull(tempIdx)?.isWhitespace() != false) optionalTypeHint else null
                    )

                    index = tempIdx
                    continue
                }

                chr == '!' -> {
//                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    if (mdInput.getOrNull(index + 1) == '[') {
                        var tempIdx = index
                        while (mdInput.getOrNull(tempIdx) != '\n') {
                            if (mdInput.getOrNull(tempIdx) == ']') break
                            tempIdx += 1
                        }
                        if (mdInput.getOrNull(tempIdx) == ']') {
                            currToken += '['
                            updateTokens(index..(index + 2))
                            index += 2
                            continue
                        }
                    }
                }

                chr == '[' -> {
//                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    var tempIdx = index
                    val isFootNoteType = mdInput.getOrNull(tempIdx + 1) == '^'
                    while (mdInput.getOrNull(tempIdx) != '\n') {
                        if (mdInput.getOrNull(tempIdx) == ']') break
                        tempIdx += 1
                    }
                    if (mdInput.getOrNull(tempIdx) == ']') {
                        if (isFootNoteType) {
                            val isFootNote = mdInput.getOrNull(tempIdx + 1) == ':'
                            if (isFootNote) {
                                currToken += mdInput.substring(index + 1, tempIdx + 2)
                                updateTokens(index..<(tempIdx + 2), tokenHint = MDTokenHint.IsRef)
                                tempIdx += 1
                            } else {
                                currToken += mdInput.substring(index + 1, tempIdx + 1)
                                updateTokens(index..<(tempIdx + 1), tokenHint = MDTokenHint.IsRefLink)
                            }
                            index = tempIdx
                        } else {
                            updateTokens(index..(index + 1), tokenHint = MDTokenHint.IsLinkStart)
                        }
                    }
                }

                chr == ']' -> {
//                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    if (mdInput.getOrNull(index + 1) == '(') {
                        currToken += mdInput[index + 1]
                        updateTokens(index..<(index + 2))
                        index += 2

                        var tempIndex = index
                        if (mdInput[tempIndex] == '<') {
                            while (tempIndex < mdInput.length && mdInput[tempIndex] != '>') tempIndex += 1
                        } else {
                            while (tempIndex < mdInput.length && mdInput[tempIndex] != ')') tempIndex += 1
                        }

                        // Get only the link.
                        val linkUri = mdInput.substring(index, tempIndex).trim('<', '>')
                        tokens += MDToken.LINK_URI(linkUri, index..<(tempIndex + 1))
                        if (mdInput[tempIndex] == '>') tempIndex += 1
                        index = tempIndex

                        continue
                    }
                }

                chr == ')' -> {
                    var linkStartBeforeLinkEnd = false
                    for (i in tokens.asReversed()) {
                        if (i is MDToken.LINK_START) {
                            linkStartBeforeLinkEnd = true
                            break
                        } else if (i is MDToken.LINK_END) {
                            break
                        }
                    }
                    currToken += chr
                    if (linkStartBeforeLinkEnd) {
                        updateTokens(index..<(index + 1), tokenHint = MDTokenHint.IsLinkEnd)
                    } else {
                        updateTokens(index..<(index + 1))
                    }
                }

                chr == '\n' -> {
                    currToken += chr
                    index += 1
                    updateTokens((index - currToken.length)..index)
                    continue
                }

                chr.isWhitespace() -> {
                    currToken += chr
                    val oldIndex = index
                    index += 1
                    while (index < mdInput.length && mdInput[index].isWhitespace() && mdInput[index] != '\n') {
                        currToken += mdInput[index]
                        index += 1
                    }
                    updateTokens(oldIndex..< index + 1)
                    continue
                }

                !chr.isWordPart() -> {
                    currToken += chr
                    updateTokens(index..(index + 1))
                }

                else -> {
                    val lastChar = currToken.lastOrNull()
                    if (lastChar == null || lastChar.isWordPart()) {
                        currToken += chr
                    } else {
                        updateTokens((index - currToken.length)..index)
                        currToken += chr
                    }
                    while (mdInput.getOrNull(index + 1)?.isWordPart() == true) {
                        index += 1
                        if (index >= mdInput.length) {
                            break
                        }
                        currToken += mdInput[index]
                    }
                    updateTokens((index - currToken.length)..index)
                }
            }
            index += 1
        }

        updateTokens((index - currToken.length)..<index)
        tokens.push(MDToken.EOF(index..index))
        return tokens
    }

    private fun getListTypeHint(isOrdered: Boolean): MDTokenHint? {
        var wsBeforeNewline: Int? = null
        if (tokens.isEmpty()) {
            return if (!isOrdered) MDTokenHint.IsUListStart(0)
            else MDTokenHint.IsOListStart(0)
        }
        for (i in tokens.asReversed()) {
            if (i is MDToken.TEXT) {
                if (i.text.isBlank()) wsBeforeNewline = i.text.length
                else break
            } else if (wsBeforeNewline != null && i !is MDToken.NEWLINE) {
                wsBeforeNewline = null
                break
            } else if (i is MDToken.NEWLINE) {
                wsBeforeNewline = wsBeforeNewline ?: 0
                break
            }
        }

        return if (wsBeforeNewline != null) {
            if (!isOrdered) MDTokenHint.IsUListStart(wsBeforeNewline)
            else MDTokenHint.IsOListStart(wsBeforeNewline)
        } else null
    }

    val output: List<MDToken>
        get() = tokenise()
}

fun Char.isWordPart(): Boolean = this.isLetterOrDigit() || this == '-' || this == '_'

fun String.mdTokens(): List<MDToken> = MDTokeniser(this).output
