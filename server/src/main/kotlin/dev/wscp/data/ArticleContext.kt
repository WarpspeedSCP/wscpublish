package dev.wscp.data

import io.ktor.http.*
import kotlinx.html.*
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

class ArticleFrontmatter(
    val title: String,
    val description: String,
    val date: String,
    val slug: String,
    val keywords: List<String>,
    val type: String,
    val categories: List<String>,
    val mediaLocation: File? = null,
    val draft: Boolean = true,
    val extras: Map<String, String>,
)

class LineColPosition(val startLine: Int, val startCol: Int, val endLine: Int, val endCol: Int)

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
    // ([^\n \t]`)|(`[^\n \t])
    data class SINGLE_GRAVE(override val span: IntRange) : MDToken(span)

    // (\n```)|(```\n)
    data class TRIPLE_GRAVE(override val span: IntRange, val language: String? = null) : MDToken(span)

    // ([^\n \t]*)|(*[^\n \t])
    data class SINGLE_ASTERISK(override val span: IntRange) : MDToken(span)

    // ([^\n \t]**)|(**[^\n \t])
    data class DOUBLE_ASTERISK(override val span: IntRange) : MDToken(span)

    // ([^\n \t]***)|(***[^\n \t])
    data class TRIPLE_ASTERISK(override val span: IntRange) : MDToken(span)

    // _
    data class SINGLE_UNDERSCORE(override val span: IntRange) : MDToken(span)

    // -\s+
    data class UL_ITEM(val level: Int, override val span: IntRange) : MDToken(span)
    data class OL_ITEM(val level: Int, override val span: IntRange) : MDToken(span)

    // ---
    data class TRIPLE_HYPHEN(override val span: IntRange) : MDToken(span)

    // ===
    data class TRIPLE_EQUALS(override val span: IntRange) : MDToken(span)

    // ^<[^>]*>$
    data class HTML_TAG(val tag: String, override val span: IntRange) : MDToken(span)

    data class TEXT(val text: String, override val span: IntRange) : MDToken(span)

    // ^![.*$
    data class IMAGE_START(override val span: IntRange) : MDToken(span)

    // ^[.*$
    data class LINK_START(override val span: IntRange) : MDToken(span)

    // ^](.*$
    data class LINK_INTERSTICE(override val span: IntRange) : MDToken(span)

    // ^).*$
    data class LINK_END(override val span: IntRange) : MDToken(span)

    sealed class HTag(override val span: IntRange) : MDToken(span) {
        // ^# .*
        data class H1(override val span: IntRange) : HTag(span)

        data class H2(override val span: IntRange) : HTag(span)

        data class H3(override val span: IntRange) : HTag(span)

        data class H4(override val span: IntRange) : HTag(span)

        data class H5(override val span: IntRange) : HTag(span)

        data class H6(override val span: IntRange) : HTag(span)
    }

    data class NEWLINE(override val span: IntRange) : MDToken(span)

    data class ESCAPE(override val span: IntRange, val escapedChar: Char) : MDToken(span)

    data class EOF(override val span: IntRange) : MDToken(span)
}

sealed interface MDTokenHint {
    object IsLinkStart : MDTokenHint
    object IsLinkEnd : MDTokenHint
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
                currToken.startsWith('\\') -> MDToken.ESCAPE(span, currToken[1])
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

                currToken == "**" -> MDToken.DOUBLE_ASTERISK(span)
                currToken == "***" -> MDToken.TRIPLE_ASTERISK(span)
                currToken == "1." && tokenHint is MDTokenHint.IsOListStart -> MDToken.OL_ITEM(tokenHint.indent, span)
                currToken == "-" && tokenHint is MDTokenHint.IsUListStart -> MDToken.UL_ITEM(tokenHint.indent, span)
                currToken == "![" -> MDToken.IMAGE_START(span)
                currToken == "[" && tokenHint == MDTokenHint.IsLinkStart -> MDToken.LINK_START(span)
                currToken == "]" && tokenHint == MDTokenHint.IsLinkEnd -> MDToken.LINK_END(span)
                currToken == "](" -> MDToken.LINK_INTERSTICE(span)
                currToken == "---" -> MDToken.TRIPLE_HYPHEN(span)
                currToken == "====" -> MDToken.TRIPLE_EQUALS(span)
                currToken == "#" -> MDToken.HTag.H1(span)
                currToken == "##" -> MDToken.HTag.H2(span)
                currToken == "###" -> MDToken.HTag.H2(span)
                currToken == "####" -> MDToken.HTag.H4(span)
                currToken == "#####" -> MDToken.HTag.H5(span)
                currToken == "######" -> MDToken.HTag.H6(span)
                currToken.startsWith("<") -> MDToken.HTML_TAG(currToken, span)
                else -> {
                    MDToken.TEXT(currToken, span)
                }
            }
        )
        currToken = ""
    } else Unit

    private fun tokenise(): List<MDToken> {
        var index = 0
        while (index < input.length) {

            val chr = input[index]
            when {
                chr == '\\' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    currToken += input.getOrNull(index + 1) ?: ""
                    updateTokens(index..(index + 2), tokenHint = MDTokenHint.IsLinkStart)
                    index += 2
                    continue
                }

                chr == '-' -> {
                    updateTokens((index - currToken.length)..index)
                    if (input.getOrNull(index + 1)?.isWhitespace() == true) {
                        val optionalTypeHint = getListTypeHint(false)
                        currToken += "-"

                        updateTokens(index..(index + 2), tokenHint = optionalTypeHint)
                    }
                }

                chr == '1' -> {
                    updateTokens((index - currToken.length)..index)
                    if (input.getOrNull(index + 1) == '.' && input.getOrNull(index + 2)?.isWhitespace() == true) {
                        val optionalTypeHint = getListTypeHint(true)
                        currToken += "1."

                        updateTokens(index..(index + 2), tokenHint = optionalTypeHint)
                        index += 2
                        continue
                    }
                }

                chr == '*' || chr == '~' || chr == '#' || chr == '_' || chr == '`' -> {
                    updateTokens((index - currToken.length)..index)
                    val optionalTypeHint = getListTypeHint(false)

                    var tempIdx = index
                    while (input.getOrNull(tempIdx) == chr) {
                        if (tempIdx - index > 3) {
                            break
                        } else {
                            currToken += chr
                            tempIdx += 1
                        }
                    }

                    updateTokens(
                        (tempIdx - currToken.length)..tempIdx,
                        if (input.getOrNull(tempIdx)?.isWhitespace() != false) optionalTypeHint else null
                    )

                    index = tempIdx
                    continue
                }

                chr == '!' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    if (input.getOrNull(index + 1) == '[') {
                        var tempIdx = index
                        while (input.getOrNull(tempIdx) != '\n') {
                            if (input.getOrNull(tempIdx) == ']') break
                            tempIdx += 1
                        }
                        if (input.getOrNull(tempIdx) == ']') {
                            currToken += '['
                            updateTokens(index..(index + 2))
                            index += 2
                            continue
                        }
                    }
                }

                chr == '[' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    var tempIdx = index
                    while (input.getOrNull(tempIdx) != '\n') {
                        if (input.getOrNull(tempIdx) == ']') break
                        tempIdx += 1
                    }
                    if (input.getOrNull(tempIdx) == ']') {
                        updateTokens(index..(index + 1), tokenHint = MDTokenHint.IsLinkStart)
                    }
                }

                chr == ']' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    if (input.getOrNull(index + 1) == '(') {
                        currToken += input[index + 1]
                        updateTokens(index..(index + 2))
                        index += 2
                        continue
                    }
                }

                chr == ')' -> {
                    updateTokens((index - currToken.length)..index)
                    var linkStartBeforeLinkEnd = false
                    for (i in tokens.asReversed()) {
                        if (i is MDToken.LINK_START) {
                            linkStartBeforeLinkEnd = true
                            break
                        } else if (i is MDToken.LINK_END) {
                            break
                        }
                    }
                    if (linkStartBeforeLinkEnd) {
                        updateTokens(index..(index + 1), tokenHint = MDTokenHint.IsLinkEnd)
                    }
                }

                chr == '\n' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    index += 1
                    updateTokens((index - currToken.length)..index)
                    continue
                }

                chr.isWhitespace() -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    index += 1
                    while (index < input.length && input[index].isWhitespace() && input[index] != '\n') {
                        currToken += input[index]
                        index += 1
                    }
                    updateTokens((index - currToken.length)..index)
                    continue
                }

                !chr.isWordPart() -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                }

                else -> {
                    val lastChar = currToken.lastOrNull()
                    if (lastChar == null || lastChar.isWordPart()) {
                        currToken += chr
                    } else {
                        updateTokens((index - currToken.length)..index)
                        currToken += chr
                    }
                    while (input.getOrNull(index + 1)?.isWordPart() == true) {
                        index += 1
                        if (index >= input.length) {
                            break
                        }
                        currToken += input[index]
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
            if (i is MDToken.TEXT && i.text.isBlank()) {
                wsBeforeNewline = i.text.length
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

sealed interface MDFormat {
    data class H1(val inner: MutableList<MDFormat>) : MDFormat
    data class H2(val inner: MutableList<MDFormat>) : MDFormat
    data class H3(val inner: MutableList<MDFormat>) : MDFormat
    data class H4(val inner: MutableList<MDFormat>) : MDFormat
    data class H5(val inner: MutableList<MDFormat>) : MDFormat
    data class H6(val inner: MutableList<MDFormat>) : MDFormat

    object HorizontalRule : MDFormat
    object LineBreak : MDFormat

    data class Paragraph(val inner: MutableList<MDFormat>) : MDFormat
    data class Div(val inner: MutableList<MDFormat>) : MDFormat {
        constructor(vararg items: MDFormat) : this(items.toMutableList())
    }
    data class Bold(val inner: MutableList<MDFormat>) : MDFormat
    data class Italic(val inner: MutableList<MDFormat>) : MDFormat
    data class Strikethrough(val inner: MutableList<MDFormat>) : MDFormat

    data class Underline(val inner: MutableList<MDFormat>) : MDFormat
    data class Link(val inner: MutableList<MDFormat>, val url: String) : MDFormat
    data class Code(val inner: MutableList<MDFormat>, val language: String = "") : MDFormat
    data class Text(val text: String) : MDFormat
    sealed class MDList(open val items: MutableList<MDFormat>, open val level: Int) : MDFormat
    data class UList(override val items: MutableList<MDFormat>, override val level: Int) : MDList(items, level)
    data class OList(override val items: MutableList<MDFormat>, override val level: Int) : MDList(items, level)
    data class Quote(val inner: MutableList<MDFormat>) : MDFormat
    data class Image(val url: String, val alt: String) : MDFormat


//    data class MDTable(val columns: List<String>, val rows: List<List<MDFormat>>) : MDFormat
}

fun <T> MutableList<T>.pop(): T? = this.removeLastOrNull()
fun <T> MutableList<T>.push(item: T) = this.add(item)
fun <T> MutableList<T>.top(): T? = this.lastOrNull()

class MarkdownTreeMaker {
    fun parse(input: String): List<MDFormat> {
        val tokens = input.mdTokens()
        return parse(tokens, LineColTracker(input))
    }

    fun parse(input: List<MDToken>, lineColTracker: LineColTracker): MutableList<MDFormat> {
        var pos = 0

        val output = mutableListOf<MDFormat>()
        var currList: MDFormat.MDList? = null
        while (pos < input.size) {
            when (val curr = input[pos]) {
                is MDToken.HTag -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.NEWLINE }
                    val inner = parse(rest, lineColTracker)
                    val parentDiv = output.findLast { it is MDFormat.Div } as MDFormat.Div?
                    val tag = when (curr) {
                        is MDToken.HTag.H1 -> MDFormat.H1(inner)
                        is MDToken.HTag.H2 -> MDFormat.H2(inner)
                        is MDToken.HTag.H3 -> MDFormat.H3(inner)
                        is MDToken.HTag.H4 -> MDFormat.H4(inner)
                        is MDToken.HTag.H5 -> MDFormat.H5(inner)
                        is MDToken.HTag.H6 -> MDFormat.H6(inner)
                    }
                    parentDiv?.inner?.push(tag)
                        ?: throw IllegalStateException("No parent div found for ${curr.javaClass.simpleName}!")
                    pos += inner.size // The newline will be consumed outside the when block.

                }

                is MDToken.DOUBLE_ASTERISK -> {
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile { it !is MDToken.DOUBLE_ASTERISK && it !is MDToken.TRIPLE_ASTERISK }
                    val inner =
                        if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.DOUBLE_ASTERISK) {
                            parse(rest, lineColTracker) as MutableList<MDFormat>
                        } else if (input[pos + rest.size + 1] is MDToken.TRIPLE_ASTERISK) {
                            parse(
                                rest + MDToken.SINGLE_ASTERISK(input[pos + rest.size + 1].span),
                                lineColTracker
                            ) as MutableList<MDFormat>
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    val tag = MDFormat.Bold(inner)
                    output.push(tag)
                    pos += inner.size
                    // We want to eat the closing token as well;
                    // this will happen right after the when ends.
                }

                is MDToken.HTML_TAG -> TODO()
                is MDToken.IMAGE_START -> TODO()
                is MDToken.LINK_END -> TODO()
                is MDToken.LINK_INTERSTICE -> TODO()
                is MDToken.LINK_START -> TODO()
                is MDToken.NEWLINE -> {
                    if (input.getOrNull(pos + 1) is MDToken.NEWLINE) {
                        output.push(MDFormat.LineBreak)
                        pos += 1
                    } else {
                        output.push(MDFormat.Text("\n"))
                    }
                }
                is MDToken.SINGLE_ASTERISK -> {
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile { it !is MDToken.SINGLE_ASTERISK && it !is MDToken.TRIPLE_ASTERISK }
                    val inner =
                        if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_ASTERISK) {
                            parse(rest, lineColTracker) as MutableList<MDFormat>
                        } else if (input[pos + rest.size + 1] is MDToken.TRIPLE_ASTERISK) {
                            parse(
                                rest + MDToken.DOUBLE_ASTERISK(input[pos + rest.size + 1].span),
                                lineColTracker
                            ) as MutableList<MDFormat>
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    val tag = MDFormat.Italic(inner)
                    output.push(tag)
                    pos += inner.size // We want to eat the closing token as well. That will happen after we exit the when.
                }

                is MDToken.SINGLE_GRAVE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.SINGLE_GRAVE }
                    val inner = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_GRAVE) {
                        parse(rest, lineColTracker) as MutableList<MDFormat>
                    } else {
                        throw IllegalStateException("unreachable")
                    }
                    val tag = MDFormat.Code(inner)
                    output.push(tag)
                    pos += inner.size // We want to eat the closing token as well. That will happen after we exit the when.

                }

                is MDToken.OL_ITEM -> {

                }

                is MDToken.UL_ITEM -> {
                    val (rest, atEnd) = collectListTokensTillNextItemOnLevel(
                        input,
                        pos,
                        curr
                    )

                    pos += rest.size // We will be adding 1 to the current position after the when ends.

                    val res = if (currList != null) {
                        val result: MutableList<MDFormat> = if (currList.level == curr.level) {
                            parse(rest, lineColTracker)
                        } else if (currList.level < curr.level) {
                            val nestedList = MDFormat.UList(mutableListOf(), curr.level)
                            nestedList.items.addAll(parse(rest, lineColTracker))
                            mutableListOf(nestedList)
                        } else mutableListOf()

                        MDFormat.Div(result) // Stay on the current level.
                    } else {
                        currList = MDFormat.UList(mutableListOf(), curr.level)
                        MDFormat.Div(parse(rest, lineColTracker))
                    }

                    currList.items.push(res)

                    if (atEnd) {
                        output.push(currList)
                        currList = null
                    }
                }

                is MDToken.SINGLE_UNDERSCORE -> {

                }
                is MDToken.ESCAPE -> {
                    val currParent = output.top()
                    val currFmt = MDFormat.Text(URLEncoder.encode(curr.escapedChar.toString(), "UTF-8"))
                    if (currParent is MDFormat.Div) {
                        currParent.inner.push(currFmt)
                    } else {
                        output.push(MDFormat.Div(inner = mutableListOf(currFmt)))
                    }
                }

                is MDToken.TEXT -> {
                    val currFmt = MDFormat.Text(curr.text)
                    output.push(currFmt)
                }

                is MDToken.TRIPLE_ASTERISK -> TODO()
                is MDToken.TRIPLE_EQUALS -> TODO()
                is MDToken.TRIPLE_GRAVE -> TODO()
                is MDToken.TRIPLE_HYPHEN -> TODO()
                is MDToken.EOF -> {}
            }

            pos += 1
        }

        return output
    }

    private fun collectListTokensTillNextItemOnLevel(
        input: List<MDToken>,
        pos: Int,
        curr: MDToken.UL_ITEM
    ): Pair<List<MDToken>, Boolean> {
        // I just wanted an easy way to modify a captured value lololol
        val prevWasNewline = AtomicBoolean(false)

        val rest = input.subList(pos + 1, input.size).takeWhile {
            // A side effect of this check is that for token fragments (when parsing a sublist for example)
            // this code exits with prevWasNewline set to true once the list is exhausted, despite
            // there not being multiple newlines.
            // This is actually correct because it causes the sublist to terminate
            // and be added into the parent list.
            if (it is MDToken.NEWLINE || it is MDToken.EOF) {
                if (prevWasNewline.get().not()) {
                    prevWasNewline.set(true)
                    true
                } else {
                    false // Two consecutive newlines ends the list item.
                }
                // Indicates we're either starting a new list item or moving back by one level.
            } else if (it is MDToken.UL_ITEM && it.level <= curr.level) {
                prevWasNewline.set(false)
                false
            }
            else {
                prevWasNewline.set(false) // we only want to break if we see consecutive newlines.
                true
            }
        }.dropLastWhile { it !is MDToken.NEWLINE && it !is MDToken.EOF } // The current list item only consumes items up till the newline.

        return rest to prevWasNewline.get()
    }

    val String.isMDSignificant: Boolean
        get() = when (this) {
            "#", "##", "###", "####", "#####", "######" -> true
            "*", "**", "***" -> true
            "[", "](", "![" -> true
            else -> false
        }
}


data class ArticleContext(
    val pageContext: PageContext,
    val contentType: ContentType = ContentType("text", "markdown"),
    val frontmatter: ArticleFrontmatter,
    val content: String = "",
) {

    fun DIV.mdFormat(line: String): DIV {
        return this
    }

    fun DIV.htmlFormatContent(): kotlinx.html.DIV {
        return this@htmlFormatContent
    }
}
