package dev.wscp.data

import dev.wscp.markdown.*
import dev.wscp.utils.pop
import dev.wscp.utils.push
import io.ktor.http.*
import kotlinx.html.DIV
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.getOrNull
import kotlin.collections.plus
import kotlin.collections.takeWhile
import kotlin.collections.withIndex

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

interface MDVisitor<T> {
    fun visitH1(node: MDFormat.H1): T?
    fun visitH2(node: MDFormat.H2): T?
    fun visitH3(node: MDFormat.H3): T?
    fun visitH4(node: MDFormat.H4): T?
    fun visitH5(node: MDFormat.H5): T?
    fun visitH6(node: MDFormat.H6): T?

    fun visitHorizontalRule(node: MDFormat.HorizontalRule): T?
    fun visitLineBreak(node: MDFormat.LineBreak): T?

    fun visitBold(node: MDFormat.Bold): T?
    fun visitItalic(node: MDFormat.Italic): T?
    fun visitStrikethrough(node: MDFormat.Strikethrough): T?
    fun visitUnderline(node: MDFormat.Underline): T?

    fun visitLink(node: MDFormat.Link): T?
    fun visitCode(node: MDFormat.Code): T?

    fun visitParagraph(node: MDFormat.Paragraph): T?
    fun visitDiv(node: MDFormat.Div): T?

    fun visitUList(node: MDFormat.UList): T?
    fun visitOList(node: MDFormat.OList): T?

    fun visitImage(node: MDFormat.Image): T?

    fun visitCustomHtml(node: MDFormat.CustomHtml): T?
    fun visitCustomScript(node: MDFormat.CustomScript): T?

    fun visit(node: MDFormat): T? {
        return null
    }
}

sealed interface MDFormat {
    fun <T> accept(visitor: MDVisitor<T>): T? = this.acceptGeneric(visitor)

    fun <T> acceptGeneric(visitor: MDVisitor<T>): T? =
        when (this) {
            is H1 -> visitor.visitH1(this)
            is H2 -> visitor.visitH2(this)
            is H3 -> visitor.visitH3(this)
            is H4 -> visitor.visitH4(this)
            is H5 -> visitor.visitH5(this)
            is H6 -> visitor.visitH6(this)

            is HorizontalRule -> visitor.visitHorizontalRule(this)
            is LineBreak -> visitor.visitLineBreak(this)

            is Bold -> visitor.visitBold(this)
            is Italic -> visitor.visitItalic(this)
            is Strikethrough -> visitor.visitStrikethrough(this)
            is Underline -> visitor.visitUnderline(this)

            is Link -> visitor.visitLink(this)
            is Code -> visitor.visitCode(this)

            is Paragraph -> visitor.visitParagraph(this)
            is Div -> visitor.visitDiv(this)

            is UList -> visitor.visitUList(this)
            is OList -> visitor.visitOList(this)

            is Image -> visitor.visitImage(this)
            is CustomHtml -> visitor.visitCustomHtml(this)
            is CustomScript -> visitor.visitCustomScript(this)

            is Quote -> TODO()
            is Text -> TODO()
        }

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
    data class Link(val inner: MutableList<MDFormat>, val uri: URI?) : MDFormat
    data class Code(val inner: MutableList<MDFormat>, val language: String? = null) : MDFormat
    data class Text(val text: String) : MDFormat
    sealed class MDList(open val items: MutableList<MDFormat>, open val level: Int) : MDFormat
    data class UList(override val items: MutableList<MDFormat>, override val level: Int) : MDList(items, level)
    data class OList(override val items: MutableList<MDFormat>, override val level: Int) : MDList(items, level)
    data class Quote(override val items: MutableList<MDFormat>, override val level: Int) : MDList(items, level)
    data class Image(val alt: MutableList<MDFormat>, val uri: URI?) : MDFormat
    data class CustomHtml(val tag: MDToken.HTML_TAG, val inner: MutableList<MDFormat>) : MDFormat
    data class CustomScript(val tag: MDToken.SCRIPT_TAG) : MDFormat

//    data class MDTable(val columns: List<String>, val rows: List<List<MDFormat>>) : MDFormat
}

class MarkdownTreeMaker {
    fun parse(input: String): List<MDFormat> {
        val tokens = input.mdTokens()
        return parse(tokens, LineColTracker(input))
    }

    fun parse(input: List<MDToken>, lineColTracker: LineColTracker): MutableList<MDFormat> {
        var pos = 0

        val output = mutableListOf<MDFormat>()
        var currList: MDFormat.MDList? = null
        var currBQuote: MDFormat.Quote? = null

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
                is MDToken.SCRIPT_TAG -> {
                    output.push(MDFormat.CustomScript(curr))
                }
                is MDToken.HTML_TAG -> {
                    val tagStack = mutableListOf<MDToken.HTML_TAG>()
                    val iter = input.subList(pos + 1, input.size)
                    var endIndex = -1
                    for ((idx, i) in iter.withIndex()) {
                        if (i is MDToken.HTML_TAG && !i.selfClosing) {
                            tagStack += i
                        } else if (i is MDToken.HTML_CLOSE_TAG) {
                            if (tagStack.lastOrNull()?.tag == i.tag) tagStack.pop()
                            else if (tagStack.isEmpty() && i.tag == curr.tag) {
                                endIndex = idx
                                break
                            }
                        }
                    }

                    if (curr.selfClosing) {
                        output.push(MDFormat.CustomHtml(tag = curr, inner = mutableListOf()))
                        pos += 1
                        continue
                    }

                    if (endIndex < 0) throw IllegalStateException("No closing tag for $curr, at ${lineColTracker.getLineColForSpan(curr.span)}")

                    val rest = input.subList(pos + 1, endIndex + 1)

                    val contents = parse(rest, lineColTracker)
                    output.push(MDFormat.CustomHtml(tag = curr, inner = contents))
                    pos = endIndex
                }
                is MDToken.LINK_URI -> {}
                is MDToken.HTML_CLOSE_TAG -> {} // never encountered, we swallow these in the case above.
                is MDToken.LINK_END -> {}
                is MDToken.LINK_INTERSTICE -> {}
                is MDToken.LINK_START, is MDToken.IMAGE_START -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.LINK_END }
                    pos += rest.size
                    if (input.getOrNull(pos + 1) is MDToken.LINK_END) {
                        pos += 1
                    }

                    val url = rest.find { it is MDToken.LINK_URI } as? MDToken.LINK_URI
                    val desc = rest.takeWhile { it !is MDToken.LINK_INTERSTICE }

                    val parsedDesc = parse(desc, lineColTracker)
                    val res = if (curr is MDToken.IMAGE_START) {
                        MDFormat.Image(parsedDesc, url?.uri)
                    } else {
                        MDFormat.Link(parsedDesc, url?.uri)
                    }
                    output.push(res)
                }
                is MDToken.NEWLINE -> {
                    if (input.getOrNull(pos + 1) is MDToken.NEWLINE) {
                        output.push(MDFormat.LineBreak)
                        pos += 1
                    } else {
                        output.push(MDFormat.Text("\n"))
                    }
                }

                is MDToken.TRIPLE_ASTERISK -> {
                    val gotSingleFirst = AtomicBoolean(false)
                    val gotDoubleFirst = AtomicBoolean(false)
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile {
                            if (it is MDToken.SINGLE_ASTERISK) {
                                if (gotDoubleFirst.get()) false
                                else {
                                    gotSingleFirst.set(true)
                                    true
                                }
                            } else if (it is MDToken.DOUBLE_ASTERISK) {
                                if (gotSingleFirst.get()) false
                                else {
                                    gotDoubleFirst.set(true)
                                    true
                                }
                            } else if (it is MDToken.TRIPLE_ASTERISK) {
                                !gotSingleFirst.get() && !gotDoubleFirst.get()
                            } else true
                        }

                    // assume we always get a valid index, guaranteed by the check above.
                    val result = if (gotSingleFirst.get()) {
                        val singleIndex = rest.indexOfFirst { it is MDToken.SINGLE_ASTERISK }
                        val italicSpan = rest.subList(0, singleIndex)
                        val italicElement = MDFormat.Italic(parse(italicSpan, lineColTracker))
                        val boldedResult = parse(rest.subList(singleIndex + 1, rest.size), lineColTracker)
                        val finalResult = MDFormat.Bold(mutableListOf<MDFormat>(italicElement).apply { addAll(boldedResult) })
                        finalResult
                    } else if (gotDoubleFirst.get()) {
                        val doubleIndex = rest.indexOfFirst { it is MDToken.DOUBLE_ASTERISK }
                        val boldSpan = rest.subList(0, doubleIndex)
                        val boldElement = MDFormat.Bold(parse(boldSpan, lineColTracker))
                        val italicResult = parse(rest.subList(doubleIndex + 1, rest.size), lineColTracker)
                        val finalResult = MDFormat.Italic(mutableListOf<MDFormat>(boldElement).apply { addAll(italicResult) })
                        finalResult
                    } else {
                        val finalResult = parse(rest, lineColTracker)
                        MDFormat.Bold(mutableListOf(MDFormat.Italic(finalResult)))
                    }

                    // The end element will be consumed by way of increment after the when block.
                    pos += rest.size + 1

                    output.push(result)
                }

                is MDToken.SINGLE_ASTERISK -> {
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile { it !is MDToken.SINGLE_ASTERISK && it !is MDToken.TRIPLE_ASTERISK }
                    val inner =
                        if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_ASTERISK) {
                            parse(rest, lineColTracker)
                        } else if (input[pos + rest.size + 1] is MDToken.TRIPLE_ASTERISK) {
                            parse(
                                rest + MDToken.DOUBLE_ASTERISK(input[pos + rest.size + 1].span),
                                lineColTracker
                            )
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    val tag = MDFormat.Italic(inner)
                    output.push(tag)
                    pos += rest.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                is MDToken.DOUBLE_ASTERISK -> {
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile { it !is MDToken.DOUBLE_ASTERISK && it !is MDToken.TRIPLE_ASTERISK }
                    val inner =
                        if (rest.lastOrNull() is MDToken.EOF || input.getOrNull(pos + rest.size + 1) is MDToken.DOUBLE_ASTERISK) {
                            parse(rest, lineColTracker)
                        } else if (input.getOrNull(pos + rest.size + 1) is MDToken.TRIPLE_ASTERISK) {
                            parse(
                                rest + MDToken.SINGLE_ASTERISK(input[pos + rest.size + 1].span),
                                lineColTracker
                            )
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    val tag = MDFormat.Bold(inner)
                    output.push(tag)
                    pos += rest.size + 1
                    // We want to eat the closing token as well;
                    // this will happen right after the when ends.
                }

                is MDToken.SINGLE_GRAVE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.SINGLE_GRAVE }
                    val inner = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_GRAVE) {
                        parse(rest, lineColTracker)
                    } else {
                        throw IllegalStateException("unreachable")
                    }
                    val tag = MDFormat.Code(inner)
                    output.push(tag)
                    pos += inner.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

//                is MDToken.BQUOTE -> {
//                    var (rest, atEnd) = collectListTokensTillNextItemOnLevel(
//                        input,
//                        pos,
//                        curr
//                    )
//
//                    pos += rest.size // We will be adding 1 to the current position after the when ends.
//
//                    if (input.getOrNull(pos + 1) is MDToken.NEWLINE) {
//                        pos += 1
//                        rest += input[pos]
//                    }
//
//                    val (res, newBQuote) = if (currBQuote != null) {
//                        val result: MutableList<MDFormat> = if (currBQuote.level == curr.level) {
//                            parse(rest, lineColTracker)
//                        } else if (currBQuote.level < curr.level) {
//                            val nestedList = MDFormat.Quote(mutableListOf(), curr.level)
//                            nestedList.items.addAll(parse(rest, lineColTracker))
//                            mutableListOf(nestedList)
//                        } else mutableListOf()
//
//                        MDFormat.Div(result) to currBQuote // Stay on the current level.
//                    } else {
//                        val newCurrBQuote = MDFormat.Quote(mutableListOf(), curr.level)
//                        MDFormat.Div(parse(rest, lineColTracker)) to newCurrBQuote
//                    }
//
//                    currBQuote = newBQuote
//                    currBQuote.items.push(res)
//
//                    if (atEnd) {
//                        output.push(currBQuote)
//                        currBQuote  = null
//                    }
//
//                }

                is MDToken.LIST_ITEM -> {
                    var (rest, atEnd) = collectListTokensTillNextItemOnLevel(
                        input,
                        pos,
                        curr
                    )

                    pos += rest.size // We will be adding 1 to the current position after the when ends.

                    if (input.getOrNull(pos + 1) is MDToken.NEWLINE) {
                        pos += 1
                        rest += input[pos]
                    }

                    val (res, newCurrList) = parseListItem(currList, curr, rest, lineColTracker) {
                        when (curr) {
                            is MDToken.BQUOTE -> MDFormat.Quote(mutableListOf(), level = curr.level)
                            is MDToken.OL_ITEM -> MDFormat.OList(mutableListOf(), level = curr.level)
                            is MDToken.UL_ITEM -> MDFormat.UList(mutableListOf(), level = curr.level)
                        }
                    }

                    currList = newCurrList

                    if (curr is MDToken.BQUOTE && curr.level == currList.level) {
                        currList.items.addAll(res.inner)
                    } else {
                        currList.items.push(res)
                    }

                    if (atEnd) {
                        output.push(currList)
                        currList = null
                    }
                }

                is MDToken.SINGLE_UNDERSCORE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.SINGLE_UNDERSCORE }

                    val inner = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_UNDERSCORE) {
                        parse(rest, lineColTracker)
                    } else {
                        throw IllegalStateException("unreachable")
                    }

                    val tag = MDFormat.Italic(inner)
                    output.push(tag)
                    pos += inner.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                is MDToken.ESCAPE -> {
                    val currFmt = MDFormat.Text(curr.escapedChar.toString())
                    output.push(currFmt)
                }

                is MDToken.TEXT -> {
                    if (curr.text.isBlank() && input.getOrNull(pos + 1).let { it is MDToken.UL_ITEM || it is MDToken.OL_ITEM }) {
                        // skip if this is just blank space before a list item, to avoid it getting added where it shouldn't.
                    } else {
                        val currFmt = MDFormat.Text(curr.text)
                        output.push(currFmt)
                    }
                }

                is MDToken.TRIPLE_EQUALS -> {
                    output.push(MDFormat.HorizontalRule)
                }

                is MDToken.TRIPLE_GRAVE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.TRIPLE_GRAVE }
                    val inner = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_GRAVE) {
                        parse(rest, lineColTracker)
                    } else {
                        throw IllegalStateException("unreachable")
                    }
                    val tag = MDFormat.Code(inner, language = curr.language)
                    output.push(tag)
                    pos += inner.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                // TODO: This should be different
                is MDToken.TRIPLE_HYPHEN -> {
                    output.push(MDFormat.HorizontalRule)
                }

                is MDToken.EOF -> {}
            }

            pos += 1
        }

        return output
    }

    private fun parseListItem(
        currList: MDFormat.MDList?,
        curr: MDToken.LIST_ITEM,
        rest: List<MDToken>,
        lineColTracker: LineColTracker,
        listItemCtor: () -> MDFormat.MDList
    ): Pair<MDFormat.Div, MDFormat.MDList> {
        val result = if (currList != null) {
            val result: MutableList<MDFormat> = if (currList.level == curr.level) {
                parse(rest, lineColTracker)
            } else if (currList.level < curr.level) {
                val nestedList = listItemCtor()
                nestedList.items.addAll(parse(rest, lineColTracker))
                mutableListOf(nestedList)
            } else mutableListOf()

            MDFormat.Div(result) to currList // Stay on the current level.
        } else {
            val newCurrList = listItemCtor()
            MDFormat.Div(parse(rest, lineColTracker)) to newCurrList
        }

        return result
    }

    private fun collectListTokensTillNextItemOnLevel(
        input: List<MDToken>,
        pos: Int,
        curr: MDToken.LIST_ITEM
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
            } else if (it is MDToken.LIST_ITEM && it.level <= curr.level) {
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
