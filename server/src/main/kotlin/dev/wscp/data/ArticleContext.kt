package dev.wscp.data

import io.ktor.http.*
import kotlinx.html.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class ArticleType(val id: String) {
    Tech("tech"),
    Translation("tl"),
}

class ArticleFrontmatter(
    val title: String,
    val description: String,
    val date: String,
    val slug: String,
    val keywords: List<String>,
    val type: ArticleType,
    val categories: List<String>,
    val mediaLocation: File? = null,
    val draft: Boolean = true,
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
    data class TRIPLE_GRAVE(override val span: IntRange) : MDToken(span)

    // ([^\n \t]*)|(*[^\n \t])
    data class SINGLE_ASTERISK(override val span: IntRange) : MDToken(span)

    // ([^\n \t]**)|(**[^\n \t])
    data class DOUBLE_ASTERISK(override val span: IntRange) : MDToken(span)

    // ([^\n \t]***)|(***[^\n \t])
    data class TRIPLE_ASTERISK(override val span: IntRange) : MDToken(span)

    // _
    data class SINGLE_UNDERSCORE(override val span: IntRange) : MDToken(span)

    // -\s+
    data class SINGLE_HYPHEN(val level: Int, override val span: IntRange) : MDToken(span)
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

    data class EOF(override val span: IntRange) : MDToken(span)
}

class MDTokeniser(private val input: String) {
    private val tokens = mutableListOf<MDToken>()
    private var currToken = ""
    private fun updateTokens(span: IntRange) = if (currToken.isNotEmpty()) {
        tokens.add(
            when {
                currToken == "\n" -> MDToken.NEWLINE(span)
                currToken == "`" -> MDToken.SINGLE_GRAVE(span)
                currToken == "```" -> MDToken.TRIPLE_GRAVE(span)
                currToken == "*" -> MDToken.SINGLE_ASTERISK(span)
                currToken == "**" -> MDToken.DOUBLE_ASTERISK(span)
                currToken == "***" -> MDToken.TRIPLE_ASTERISK(span)
                currToken == "1." -> {
                    var currPos = span.first
                    while (currPos > 0 && input[currPos].isWhitespace() && input[currPos] != '\n') {
                        currPos -= 1
                    }
                    val prev = input.getOrNull(currPos)
                    if (prev != null && prev != '\n') {
                        MDToken.TEXT("1.", span)
                    } else {
                        MDToken.OL_ITEM(span.first - currPos, span)
                    }
                }

                currToken == "-" -> {
                    var currPos = span.first - 1
                    while (currPos > 0 && input[currPos].isWhitespace() && input[currPos] != '\n') {
                        currPos -= 1
                    }
                    val prev = input.getOrNull(currPos)
                    if (prev != null && prev != '\n') {
                        MDToken.TEXT("-", span)
                    } else {
                        MDToken.SINGLE_HYPHEN(span.first - currPos, span)
                    }
                }

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
                    index += 1
                    continue
                }

                chr == '1' -> {
                    if (input.getOrNull(index + 1) == '.' && input.getOrNull(index + 2)?.isWhitespace() == true) {
                        updateTokens((index - currToken.length)..index)
                        currToken += "1."
                        index += 2
                    }
                }

                chr == '*' || chr == '~' || chr == '#' || chr == '_' || chr == '`' -> {
                    if (currToken.isEmpty() || currToken.endsWith(chr)) {
                        currToken += chr
                    } else {
                        updateTokens((index - currToken.length)..index)
                        currToken += chr
                    }
                }

                chr == ']' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    if (input.getOrNull(index + 1) == '(') {
                        currToken += input[index + 1]
                        index += 1
                    }
                }

                chr == '\n' -> {
                    updateTokens((index - currToken.length)..index)
                    currToken += chr
                    index += 1
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
                }
            }
            index += 1
        }

        updateTokens((index - currToken.length)..<index)
        tokens.push(MDToken.EOF(index..index))
        return tokens
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

    data class Paragraph(val inner: MutableList<MDFormat>) : MDFormat
    data class Div(val inner: MutableList<MDFormat>) : MDFormat
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

fun <T> MutableList<T>.pop(): T = this.removeLast()
fun <T> MutableList<T>.push(item: T) = this.add(item)
fun <T> MutableList<T>.top(): T = this.last()

class MarkdownTreeMaker {
    fun parse(input: String): List<MDFormat> {
        val tokens = input.mdTokens()
        return parse(tokens, LineColTracker(input))
    }

    fun parse(input: List<MDToken>, lineColTracker: LineColTracker): MutableList<MDFormat> {
        var pos = 0
        var indent = 0

        var output = mutableListOf<MDFormat>(MDFormat.Div(mutableListOf()))
        while (pos < input.size) {
            val curr = input[pos]

            when (curr) {
                is MDToken.HTag -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.NEWLINE }
                    val inner = parse(rest, lineColTracker) as MutableList<MDFormat>
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
                is MDToken.NEWLINE -> {}
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

                is MDToken.SINGLE_HYPHEN -> {
                    if (output.lastOrNull() is MDFormat.UList) {
                        val currList = output.pop() as MDFormat.UList
                        if (currList.level == curr.level) {
                            // I just wanted an easy way to modify a captured value lololol
                            val prevWasNewline = AtomicBoolean(false)

                            val rest = input.subList(pos + 1, input.size).takeWhile {
                                if (it is MDToken.NEWLINE) {
                                    if (prevWasNewline.get().not()) {
                                        prevWasNewline.set(true)
                                        true
                                    } else {
                                        false // Two consecutive newlines ends the list item.
                                    }
                                    // Indicates we're either starting a new list item or moving back by one level.
                                } else if (it is MDToken.SINGLE_HYPHEN && it.level <= curr.level) false
                                else {
                                    prevWasNewline.set(false) // we only want to break if we see consecutive newlines.
                                    true
                                }
                            }.dropLastWhile { it !is MDToken.NEWLINE } // The current list item only consumes items up till the newline, and including it.

                            pos += rest.size // We will be adding 1 to the current position after the when ends.
                            currList.items.addAll(parse(rest, lineColTracker))

                            output.push(currList) // Stay on the current level.
                        } else if (currList.level < curr.level) {
                            // we're going a level deeper, so push the current level back onto the stack.
                            output.push(currList)
                            val currList = MDFormat.UList(mutableListOf(), curr.level)
                            output.push(currList) // Stay on the current level.

                            // I just wanted an easy way to modify a captured value lololol
                            val prevWasNewline = AtomicBoolean(false)

                            val rest = input.subList(pos + 1, input.size).takeWhile {
                                if (it is MDToken.NEWLINE) {
                                    if (prevWasNewline.get().not()) {
                                        prevWasNewline.set(true)
                                        true
                                    } else {
                                        false // Two consecutive newlines ends the list item.
                                    }
                                    // Indicates we're either starting a new list item or moving back by one level.
                                } else if (it is MDToken.SINGLE_HYPHEN && it.level <= curr.level) false
                                else {
                                    prevWasNewline.set(false) // we only want to break if we see consecutive newlines.
                                    true
                                }
                            }.dropLastWhile { it !is MDToken.NEWLINE } // The current list item only consumes items up till the newline, and including it.

                            pos += rest.size // We will be adding 1 to the current position after the when ends.
                            currList.items.addAll(parse(rest, lineColTracker))


                        } else {
                            while (((output.top() as? MDFormat.MDList)?.level ?: 0) > curr.level) {
                                val currLevel = output.pop() as MDFormat.MDList
                                (output.top() as? MDFormat.MDList)?.items?.push(currLevel)
                            }
                            // I just wanted an easy way to modify a captured value lololol
                            val prevWasNewline = AtomicBoolean(false)

                            val rest = input.subList(pos + 1, input.size).takeWhile {
                                if (it is MDToken.NEWLINE) {
                                    if (prevWasNewline.get().not()) {
                                        prevWasNewline.set(true)
                                        true
                                    } else {
                                        false // Two consecutive newlines ends the list item.
                                    }
                                    // Indicates we're either starting a new list item or moving back by one level.
                                } else if (it is MDToken.SINGLE_HYPHEN && it.level <= curr.level) false
                                else {
                                    prevWasNewline.set(false) // we only want to break if we see consecutive newlines.
                                    true
                                }
                            }.dropLastWhile { it !is MDToken.NEWLINE } // The current list item only consumes items up till the newline, and including it.

                            pos += rest.size // We will be adding 1 to the current position after the when ends.
                            val currParent = output.top() as MDFormat.MDList
                            currParent.items.push(MDFormat.Div(parse(rest, lineColTracker)))
                        }
                    }
                    else {
                        val currList = MDFormat.UList(mutableListOf(), curr.level)
                        // I just wanted an easy way to modify a captured value lololol
                        val prevWasNewline = AtomicBoolean(false)

                        val rest = input.subList(pos + 1, input.size).takeWhile {
                            if (it is MDToken.NEWLINE) {
                                if (prevWasNewline.get().not()) {
                                    prevWasNewline.set(true)
                                    true
                                } else {
                                    false // Two consecutive newlines ends the list item.
                                }
                                // Indicates we're either starting a new list item or moving back by one level.
                            } else if (it is MDToken.SINGLE_HYPHEN && it.level <= curr.level) false
                            else {
                                prevWasNewline.set(false) // we only want to break if we see consecutive newlines.
                                true
                            }
                        }.dropLastWhile { it !is MDToken.NEWLINE } // The current list item only consumes items up till the newline, and including it.

                        pos += rest.size // We will be adding 1 to the current position after the when ends.
                        output.push(currList) // Stay on the current level.
                        currList.items.addAll(parse(rest, lineColTracker))



                    }
                }

                is MDToken.SINGLE_UNDERSCORE -> TODO()
                is MDToken.TEXT -> {
                    val currParent = output.top()
                    val currFmt = MDFormat.Text(curr.text)
                    if (currParent is MDFormat.Div) {
                        currParent.inner.push(currFmt)
                    } else {
                        output.push(MDFormat.Div(inner = mutableListOf(currFmt)))
                    }
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
