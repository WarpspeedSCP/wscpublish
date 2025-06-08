@file:Suppress("UNCHECKED_CAST")

package dev.wscp.data

import dev.wscp.markdown.*
import dev.wscp.utils.pop
import dev.wscp.utils.push
import dev.wscp.utils.top
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.html.A
import kotlinx.html.STRONG
import kotlinx.html.BLOCKQUOTE
import kotlinx.html.BR
import kotlinx.html.CODE
import kotlinx.html.DIV
import kotlinx.html.FlowOrHeadingContent
import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.HtmlBlockTag
import kotlinx.html.EM
import kotlinx.html.P
import kotlinx.html.HR
import kotlinx.html.HtmlBlockInlineTag
import kotlinx.html.HtmlInlineTag
import kotlinx.html.IMG
import kotlinx.html.LI
import kotlinx.html.OL
import kotlinx.html.S
import kotlinx.html.SCRIPT
import kotlinx.html.SPAN
import kotlinx.html.SUP
import kotlinx.html.Tag
import kotlinx.html.U
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.blockQuote
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.classes
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.em
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.h5
import kotlinx.html.h6
import kotlinx.html.hr
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.s
import kotlinx.html.stream.createHTML
import kotlinx.html.strong
import kotlinx.html.u
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.getOrNull
import kotlin.collections.plus
import kotlin.collections.takeWhile
import kotlin.collections.withIndex
import kotlin.reflect.full.primaryConstructor

@Serializable
data class ArticleFrontmatter(
    val title: String,
    val description: String? = null,
    val date: String = Clock.System.now().format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET),
    val slug: String,
    val keywords: List<String> = listOf(),
    val type: String,
    val categories: List<String> = listOf(),
    val mediaLocation: String? = null,
    val draft: Boolean = true,
    val extras: Map<String, String> = mapOf(),
) {
    val mediaLocationPath: Path?
        get() = if (mediaLocation != null) Path(mediaLocation) else null
}

interface MDVisitor<T> {
    fun visitH1(node: MDFormat.H1<*>): T?
    fun visitH2(node: MDFormat.H2<*>): T?
    fun visitH3(node: MDFormat.H3<*>): T?
    fun visitH4(node: MDFormat.H4<*>): T?
    fun visitH5(node: MDFormat.H5<*>): T?
    fun visitH6(node: MDFormat.H6<*>): T?

    fun visitHorizontalRule(node: MDFormat.HorizontalRule): T?
    fun visitLineBreak(node: MDFormat.LineBreak): T?
    fun visitInlineLineBreak(node: MDFormat.InlineLineBreak): T?

    fun visitBold(node: MDFormat.Bold<*>): T?
    fun visitItalic(node: MDFormat.Italic<*>): T?
    fun visitStrikethrough(node: MDFormat.Strikethrough<*>): T?
    fun visitUnderline(node: MDFormat.Underline<*>): T?

    fun visitLink(node: MDFormat.Link<*>): T?
    fun visitCode(node: MDFormat.Code<*>): T?

    fun visitMultiCode(node: MDFormat.MultilineCode<*>): T?
    fun visitParagraph(node: MDFormat.Paragraph<*>): T?
    fun visitDiv(node: MDFormat.Div<*>): T?
    fun visitUList(node: MDFormat.UList<*>): T?
    fun visitOList(node: MDFormat.OList<*>): T?

    fun visitLI(node: MDFormat.ListItem<*>): T?

    fun visitImage(node: MDFormat.Image<*>): T?
    fun visitCustomHtml(node: MDFormat.CustomHtml<*>): T?

    fun visitCustomScript(node: MDFormat.CustomScript<*>): T?
    fun visitQuote(node: MDFormat.Quote<*>): T?

    fun visitText(node: MDFormat.Text<*>): T?

    fun visit(node: MDFormat<out Tag, out Tag>): T? {
        return node.accept(this)
    }
}

sealed interface MDFormat<T: Tag, Parent: Tag> {

    val inner: MutableList<MDFormat<out Tag, T>>
        get() = mutableListOf()

    fun toHtml(input: Parent) {
        for (i in inner) {
            i.toHtml(this as T)
        }
    }

    fun <U> accept(visitor: MDVisitor<U>): U? = this.acceptGeneric(visitor)

    fun <U> acceptGeneric(visitor: MDVisitor<U>): U? =
        when (this) {
            is H1 -> visitor.visitH1(this)
            is H2 -> visitor.visitH2(this)
            is H3 -> visitor.visitH3(this)
            is H4 -> visitor.visitH4(this)
            is H5 -> visitor.visitH5(this)
            is H6 -> visitor.visitH6(this)

            is HorizontalRule -> visitor.visitHorizontalRule(this)
            is LineBreak -> visitor.visitLineBreak(this)
            is InlineLineBreak -> visitor.visitInlineLineBreak(this)

            is Bold -> visitor.visitBold(this)
            is Italic -> visitor.visitItalic(this)
            is Strikethrough -> visitor.visitStrikethrough(this)
            is Underline -> visitor.visitUnderline(this)

            is Link -> visitor.visitLink(this)
            is Code -> visitor.visitCode(this)

            is Paragraph -> visitor.visitParagraph(this)
            is Div -> visitor.visitDiv(this)

            is UList<*> -> visitor.visitUList(this)
            is OList<*> -> visitor.visitOList(this)
            is Quote<*> -> visitor.visitQuote(this)

            is Image -> visitor.visitImage(this)
            is CustomHtml -> visitor.visitCustomHtml(this)
            is CustomScript -> visitor.visitCustomScript(this)

            is Text -> visitor.visitText(this)
            is ListItem<*> -> visitor.visitLI(this)
            is MultilineCode<*> -> visitor.visitMultiCode(this)
        }

    sealed class Heading<T: FlowOrHeadingContent, Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, T>>) : MDFormat<T, Parent>

    data class H1<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, kotlinx.html.H1>>) : Heading<kotlinx.html.H1, Parent>(inner) {
        override fun toHtml(input: Parent) {
            input.h1 {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class H2<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, kotlinx.html.H2>>) : Heading<kotlinx.html.H2, Parent>(inner) {
        override fun toHtml(input: Parent) {
            input.h2 {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class H3<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, kotlinx.html.H3>>) : Heading<kotlinx.html.H3, Parent>(inner) {
        override fun toHtml(input: Parent) {
            input.h3 {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class H4<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, kotlinx.html.H4>>) : Heading<kotlinx.html.H4, Parent>(inner) {
        override fun toHtml(input: Parent) {
            input.h4 {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class H5<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, kotlinx.html.H5>>) : Heading<kotlinx.html.H5, Parent>(inner) {
        override fun toHtml(input: Parent) {
            input.h5 {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class H6<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, kotlinx.html.H6>>) : Heading<kotlinx.html.H6, Parent>(inner) {
        override fun toHtml(input: Parent) {
            input.h6 {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }

    object HorizontalRule : MDFormat<HR, HtmlBlockInlineTag> {
        override fun toHtml(input: HtmlBlockInlineTag) {
            input.hr {  }
        }
    }

    object LineBreak : Block<BR, FlowContent> {
        override fun toHtml(input: FlowContent) {
            input.br
        }
    }

    object InlineLineBreak : Inline<BR, FlowContent> {
        override fun toHtml(input: FlowContent) {
            input.br
        }
    }

    sealed interface Block<T: HtmlBlockTag, Parent: FlowContent>: MDFormat<T, Parent>
    sealed interface Inline<T: HtmlInlineTag, Parent: FlowContent> : MDFormat<T, Parent>
    sealed interface BlockInline<T: HtmlBlockInlineTag, Parent: FlowContent>: Block<T, Parent>, Inline<T, Parent>

    data class Paragraph<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, P>>) : BlockInline<P, Parent> {
        override fun toHtml(input: Parent) {
            input.p {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }

    data class Div<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, DIV>>) : Block<DIV, Parent> {
        override fun toHtml(input: Parent) {
            input.div {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }

    data class Bold<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, STRONG>>) : BlockInline<STRONG, Parent> {
        override fun toHtml(input: Parent) {
            input.strong {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class Italic<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, EM>>) : BlockInline<EM, Parent> {
        override fun toHtml(input: Parent) {
            input.em {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class Strikethrough<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, S>>) : BlockInline<S, Parent> {
        override fun toHtml(input: Parent) {
            input.s {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class Underline<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, U>>) : BlockInline<U, Parent> {
        override fun toHtml(input: Parent) {
            input.u {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }

    data class Link<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, A>>, val uri: String?) : BlockInline<A, Parent> {
        override fun toHtml(input: Parent) {
            input.a(href = uri) {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class Code<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, CODE>>, val language: String?) : BlockInline<CODE, Parent> {
        override fun toHtml(input: Parent) {
            input.code {
                for (i in inner) {
                    i.toHtml(this)
                }
            }
        }
    }
    data class MultilineCode<Parent: FlowContent>(override val inner: MutableList<MDFormat<*, CODE>>, val language: String? = null) : BlockInline<CODE, Parent> {
        override fun toHtml(input: Parent) {
            val langClass = "lang-$language"

            input.pre {
                code {
                    if (language != null) classes = setOf("lang-$language")
                    for (i in inner) {
                        i.toHtml(this)
                    }
                }
            }
        }
    }
    data class Text<Parent: FlowContent>(val text: String) : Inline<SPAN, Parent> {
        operator fun plus(other: String) : Text<Parent> = Text(text + other)

        override fun toHtml(input: Parent) {
            input.apply {
                +text
            }
        }
    }

    data class ListItem<Parent: HtmlBlockTag>(override val inner: MutableList<MDFormat<*, LI>>) : Block<LI, Parent> {
        override fun toHtml(input: Parent) {
            val closure: LI.() -> Unit = {
                for (i in inner) {
                    i.toHtml(this)
                }
            }

            when (input) {
                is UL -> input.li(block = closure)
                is OL -> input.li(block = closure)
                else -> throw IllegalStateException("Unreachable")
            }
        }
    }

    sealed class MDList<T: HTMLTag, Self: HtmlBlockTag, Parent: FlowContent>(open val items: MutableList<out MDFormat<T, Self>>, open val level: Int) : Block<HtmlBlockTag, Parent> {
        override val inner: MutableList<MDFormat<out Tag, HtmlBlockTag>>
            get() = items as MutableList<MDFormat<out Tag, HtmlBlockTag>>
    }
    data class UList<Parent: FlowContent>(override val items: MutableList<ListItem<UL>>, override val level: Int) : MDList<LI, UL, Parent>(items, level) {
        override fun toHtml(input: Parent) {
            input.ul {
                for (i in items) {
                    i.toHtml(this)
                }
            }
        }
    }

    data class OList<Parent: FlowContent>(override val items: MutableList<ListItem<OL>>, override val level: Int) : MDList<LI, OL, Parent>(items, level) {
        override fun toHtml(input: Parent) {
            input.ol {
                for (i in items) {
                    i.toHtml(this)
                }
            }
        }
    }

    data class Quote<Parent: FlowContent>(override val items: MutableList<MDFormat<P, BLOCKQUOTE>> = mutableListOf(Paragraph(mutableListOf())), override val level: Int) : MDList<P, BLOCKQUOTE, Parent>(items, level) {
        override fun toHtml(input: Parent) {
            input.blockQuote {
                for (i in items) {
                    i.toHtml(this)
                }
            }
        }
    }

    data class Image<Parent: FlowContent>(val alt: String, val uri: String?) : BlockInline<IMG, Parent> {
        override fun toHtml(input: Parent) {
            input.img {
                alt = this@Image.alt
            }
        }
    }
    data class CustomHtml<Parent: HTMLTag>(val tag: MDToken.HTML_TAG, override val inner: MutableList<MDFormat<*, HTMLTag>>) : MDFormat<HTMLTag, Parent> {
        override fun toHtml(input: Parent) {
            val attrs = tag.attributes.map { it.key to (it.value ?: "") }.toMap().toMutableMap()

            val attrStr = tag.attributes.entries.joinToString(" ") { "${it.key}${if (it.value != null) "=\"${it.value}\"" else "" }" }
            when (tag.tag.lowercase()) {
                "div" -> kotlinx.html.DIV::class
                "span" -> kotlinx.html.SPAN::class
                "p" -> kotlinx.html.P::class
                "section" -> kotlinx.html.SECTION::class
                "article" -> kotlinx.html.ARTICLE::class
                "main" -> kotlinx.html.MAIN::class
                "header" -> kotlinx.html.HEADER::class
                "footer" -> kotlinx.html.FOOTER::class
                "nav" -> kotlinx.html.NAV::class
                "aside" -> kotlinx.html.ASIDE::class
                "a" -> kotlinx.html.A::class
                "link" -> kotlinx.html.LINK::class
                "button" -> kotlinx.html.BUTTON::class
                "img" -> kotlinx.html.IMG::class
                "picture" -> kotlinx.html.PICTURE::class
                "svg" -> kotlinx.html.SVG::class
                "canvas" -> kotlinx.html.CANVAS::class
                "video" -> kotlinx.html.VIDEO::class
                "audio" -> kotlinx.html.AUDIO::class
                "table" -> kotlinx.html.TABLE::class
                "caption" -> kotlinx.html.CAPTION::class
                "tr" -> kotlinx.html.TR::class
                "td" -> kotlinx.html.TD::class
                "th" -> kotlinx.html.TH::class
                "thead" -> kotlinx.html.THEAD::class
                "tbody" -> kotlinx.html.TBODY::class
                "tfoot" -> kotlinx.html.TFOOT::class
                "h1" -> kotlinx.html.H1::class
                "h2" -> kotlinx.html.H2::class
                "h3" -> kotlinx.html.H3::class
                "h4" -> kotlinx.html.H4::class
                "h5" -> kotlinx.html.H5::class
                "h6" -> kotlinx.html.H6::class
                "ul" -> kotlinx.html.UL::class
                "ol" -> kotlinx.html.OL::class
                "li" -> kotlinx.html.LI::class
                "dl" -> kotlinx.html.DL::class
                "dt" -> kotlinx.html.DT::class
                "dd" -> kotlinx.html.DD::class
                "form" -> kotlinx.html.FORM::class
                "input" -> kotlinx.html.INPUT::class
                "textarea" -> kotlinx.html.TEXTAREA::class
                "select" -> kotlinx.html.SELECT::class
                "option" -> kotlinx.html.OPTION::class
                "label" -> kotlinx.html.LABEL::class
                "fieldset" -> kotlinx.html.FIELDSET::class
                "legend" -> kotlinx.html.LEGEND::class
                "pre" -> kotlinx.html.PRE::class
                "code" -> kotlinx.html.CODE::class
                "blockquote" -> kotlinx.html.BLOCKQUOTE::class
                "cite" -> kotlinx.html.CITE::class
                "hr" -> kotlinx.html.HR::class
                "br" -> kotlinx.html.BR::class
                "strong" -> kotlinx.html.STRONG::class
                "em" -> kotlinx.html.EM::class
                "b" -> kotlinx.html.B::class
                "i" -> kotlinx.html.I::class
                "u" -> kotlinx.html.U::class
                "s" -> kotlinx.html.S::class
                "small" -> kotlinx.html.SMALL::class
                "mark" -> kotlinx.html.MARK::class
                "sub" -> kotlinx.html.SUB::class
                "sup" -> kotlinx.html.SUP::class
                "iframe" -> kotlinx.html.IFRAME::class
                "embed" -> kotlinx.html.EMBED::class
                "object" -> kotlinx.html.OBJECT::class
                "style" -> kotlinx.html.STYLE::class
                "script" -> kotlinx.html.SCRIPT::class
                else -> throw IllegalStateException("Unsupported tag ${tag.tag} used!")
            }.let {
                it.primaryConstructor?.call(attrs, input.consumer)
            }?.let {
                input.consumer.onTagStart(it)

                for (i in inner) {
                    i.toHtml(it)
                }

                input.consumer.onTagEnd(it)
            } ?: run {
                throw IllegalStateException("Null output for ${tag.tag} at ${tag.span}")
            }
        }
    }
    data class CustomScript<Parent: HTMLTag>(val tag: MDToken.SCRIPT_TAG) : MDFormat<SCRIPT, Parent> {
        override fun toHtml(input: Parent) {
            val attrs = tag.attributes.map { it.key to (it.value ?: "") }.toMap().toMutableMap()
            input.consumer.apply {
                val newTag = SCRIPT(attrs, this)
                onTagStart(newTag)
                if (tag.script.isNotEmpty()) {
                    newTag.unsafe {
                        raw(tag.script)
                    }
                }
                onTagEnd(newTag)
                finalize()
            }
        }
    }

    // TODO: Add tables.
//    data class MDTable(val columns: List<String>, val rows: List<List<MDFormat>>) : MDFormat
}

class MarkdownTreeMaker {
    fun parse(input: String): List<MDFormat<*, *>> {
        val tokens = input.mdTokens()
        return parse<HTMLTag>(tokens, LineColTracker(input))
    }

    fun <ParentTagType: Tag> parse(input: List<MDToken>, lineColTracker: LineColTracker): MutableList<MDFormat<out Tag, ParentTagType>> {
        var pos = 0

        val output = mutableListOf<MDFormat<*, ParentTagType>>()
        var currList: MDFormat.MDList<*, *, FlowContent>? = null

        while (pos < input.size) {
            when (val curr = input[pos]) {
                is MDToken.HTag -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.NEWLINE }

                    val tag = (when (curr) {
                        is MDToken.HTag.H1 -> MDFormat.H1<HtmlBlockTag>(parse(rest, lineColTracker))
                        is MDToken.HTag.H2 -> MDFormat.H2(parse(rest, lineColTracker))
                        is MDToken.HTag.H3 -> MDFormat.H3(parse(rest, lineColTracker))
                        is MDToken.HTag.H4 -> MDFormat.H4(parse(rest, lineColTracker))
                        is MDToken.HTag.H5 -> MDFormat.H5(parse(rest, lineColTracker))
                        is MDToken.HTag.H6 -> MDFormat.H6(parse(rest, lineColTracker))
                    }) as MDFormat<*, ParentTagType>

                    output.push(tag)
                    pos += tag.inner.size // The newline will be consumed outside the when block.
                }
                is MDToken.SCRIPT_TAG -> {
                    output.push(MDFormat.CustomScript<HTMLTag>(curr) as MDFormat<*, ParentTagType>)
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
                        output.push(MDFormat.CustomHtml<HTMLTag>(tag = curr, inner = mutableListOf()) as MDFormat<*, ParentTagType>)
                        pos += 1
                        continue
                    }

                    if (endIndex < 0) throw IllegalStateException("No closing tag for $curr, at ${lineColTracker.getLineColForSpan(curr.span)}")

                    val rest = input.subList(pos + 1, endIndex + 1)

                    val contents = parse<HTMLTag>(rest, lineColTracker)
                    output.push(MDFormat.CustomHtml<HTMLTag>(tag = curr, inner = contents) as MDFormat<*, ParentTagType>)
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

                    val url: MDToken.LINK_URI? = rest.asReversed().findIsInstance()
                    val desc = rest.takeWhile { it !is MDToken.LINK_INTERSTICE }

                    val res = if (curr is MDToken.IMAGE_START) {
                        MDFormat.Image<FlowContent>(desc.joinToString(" ") { if (it is MDToken.TEXT) it.text else "" }, url?.uri)
                    } else {
                        MDFormat.Link(parse(desc, lineColTracker), url?.uri)
                    }
                    output.push(res as MDFormat<*, ParentTagType>)
                }

                is MDToken.LINEBREAK -> {
                    output.push(MDFormat.InlineLineBreak as MDFormat<*, ParentTagType>)
                }

                is MDToken.NEWLINE -> {
                    if (input.getOrNull(pos + 1) is MDToken.NEWLINE) {
                        var temp = pos + 1
                        while (input.getOrNull(temp) is MDToken.NEWLINE) {
                            temp += 1
                        }
                        val paragraph = mutableListOf<MDFormat<*, ParentTagType>>()
                        while (output.top() !is MDFormat.LineBreak
                            && output.top() !is MDFormat.MDList<*, *, ParentTagType>
                            && output.top() !is MDFormat.MultilineCode
                            && output.top() !is MDFormat.Div
                            && output.top() !is MDFormat.Heading
                            && output.top() !is MDFormat.HorizontalRule
                            && output.top() !is MDFormat.CustomHtml
                            && output.top() !is MDFormat.Image
                            && output.top() !is MDFormat.Paragraph<ParentTagType>
                            && output.top() is MDFormat.Inline<*, ParentTagType>
                        ) {
                            paragraph += output.pop()!!
                        }

                        if (paragraph.isEmpty()) {
                            output.push(MDFormat.LineBreak as MDFormat<*, ParentTagType>)
                        } else {
                            output.push(MDFormat.Paragraph<FlowContent>(paragraph.asReversed() as MutableList<MDFormat<*, P>>) as MDFormat<*, ParentTagType>)
                        }

                        pos = temp
                        continue
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
                        val italicElement = MDFormat.Italic<STRONG>(parse(italicSpan, lineColTracker))
                        val boldedResult = parse<STRONG>(rest.subList(singleIndex + 1, rest.size), lineColTracker)
                        val finalResult = MDFormat.Bold<FlowContent>(mutableListOf<MDFormat<*, STRONG>>(italicElement).apply { addAll(boldedResult) })
                        finalResult
                    } else if (gotDoubleFirst.get()) {
                        val doubleIndex = rest.indexOfFirst { it is MDToken.DOUBLE_ASTERISK }
                        val boldSpan = rest.subList(0, doubleIndex)
                        val boldElement = MDFormat.Bold<EM>(parse(boldSpan, lineColTracker))
                        val italicResult = parse<EM>(rest.subList(doubleIndex + 1, rest.size), lineColTracker)
                        val finalResult = MDFormat.Italic<FlowContent>(mutableListOf<MDFormat<*, EM>>(boldElement).apply { addAll(italicResult) })
                        finalResult
                    } else {
                        val finalResult = parse<EM>(rest, lineColTracker)
                        MDFormat.Bold(mutableListOf(MDFormat.Italic(finalResult)))
                    }

                    // The end element will be consumed by way of increment after the when block.
                    pos += rest.size + 1

                    output.push(result as MDFormat<*, ParentTagType>)
                }

                is MDToken.SINGLE_ASTERISK -> {
                    val inLink = AtomicBoolean(false)
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile {
                            // We skip any tokens we encounter within links, as they have higher priority.
                            if (inLink.get()) {
                                if (it is MDToken.LINK_END) {
                                    inLink.set(false)
                                    true
                                } else true
                            } else if (it is MDToken.LINK_START || it is MDToken.IMAGE_START) {
                                inLink.set(true)
                                true
                            } else {
                                it !is MDToken.SINGLE_ASTERISK && it !is MDToken.TRIPLE_ASTERISK
                            }
                        }

                    val inner =
                        if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_ASTERISK) {
                            parse<EM>(rest, lineColTracker)
                        } else if (input[pos + rest.size + 1] is MDToken.TRIPLE_ASTERISK) {
                            parse(
                                rest + MDToken.DOUBLE_ASTERISK(input[pos + rest.size + 1].span),
                                lineColTracker
                            )
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    val tag = MDFormat.Italic<FlowContent>(inner)
                    output.push(tag as MDFormat<*, ParentTagType>)
                    pos += rest.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                is MDToken.DOUBLE_ASTERISK -> {
                    val rest = input.subList(pos + 1, input.size)
                        .takeWhile { it !is MDToken.DOUBLE_ASTERISK && it !is MDToken.TRIPLE_ASTERISK }
                    val inner =
                        if (rest.lastOrNull() is MDToken.EOF || input.getOrNull(pos + rest.size + 1) is MDToken.DOUBLE_ASTERISK) {
                            parse<STRONG>(rest, lineColTracker)
                        } else if (input.getOrNull(pos + rest.size + 1) is MDToken.TRIPLE_ASTERISK) {
                            parse(
                                rest + MDToken.SINGLE_ASTERISK(input[pos + rest.size + 1].span),
                                lineColTracker
                            )
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    val tag = MDFormat.Bold<FlowContent>(inner)
                    output.push(tag as MDFormat<*, ParentTagType>)
                    pos += rest.size + 1
                    // We want to eat the closing token as well;
                    // this will happen right after the when ends.
                }

                is MDToken.SINGLE_GRAVE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.SINGLE_GRAVE }
                    val tag: MDFormat.Code<FlowContent> = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_GRAVE) {
                        MDFormat.Code(parse(rest, lineColTracker), language = curr.language)
                    } else {
                        throw IllegalStateException("unreachable")
                    }
                    output.push(tag as MDFormat<*, ParentTagType>)
                    pos += tag.inner.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                is MDToken.LIST_ITEM -> {
                    var (rest, atEnd) = collectListTokensTillNextItemOnLevel(
                        input,
                        pos,
                        curr
                    )

                    pos += rest.size // We will be adding 1 to the current position after the when ends.

                    // greedy consume newlines.
                    while (input.getOrNull(pos + 1) is MDToken.NEWLINE) {
                        pos += 1
                        rest += input[pos]
                    }

                    when (currList) {
                        is MDFormat.Quote<FlowContent> -> {
                            currList.items.first().inner.addAll(parse(rest, lineColTracker))
                        }
                        is MDFormat.UList<FlowContent> -> {
                            currList.items.add(MDFormat.ListItem<UL>(parse(rest, lineColTracker)))
                        }
                        is MDFormat.OList<FlowContent> -> {
                            currList.items.add(MDFormat.ListItem<OL>(parse(rest, lineColTracker)))
                        }
                        null -> {
                            when (curr) {
                                is MDToken.BQUOTE -> {
                                    currList = MDFormat.Quote<FlowContent>(level = curr.level).apply {
                                        val innerItems = parse<P>(rest, lineColTracker)
                                        val toAdd = (if (innerItems.size == 1 && innerItems.last() is MDFormat.Paragraph<*>) innerItems.last().inner
                                        else innerItems)
                                        items.first().inner.addAll(toAdd as List<MDFormat<*, P>>)
                                    }
                                }
                                is MDToken.OL_ITEM -> {
                                    currList = MDFormat.OList(mutableListOf(MDFormat.ListItem<OL>(parse(rest, lineColTracker))), level = curr.level)
                                }
                                is MDToken.UL_ITEM -> {
                                    currList = MDFormat.UList(mutableListOf(MDFormat.ListItem<UL>(parse(rest, lineColTracker))), level = curr.level)
                                }
                            }
                        }
                    }

                    if (atEnd) {
                        output.push(currList as MDFormat<*, ParentTagType>)
                        currList = null
                    }
                }

                is MDToken.SINGLE_UNDERSCORE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.SINGLE_UNDERSCORE }

                    val inner = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_UNDERSCORE) {
                        parse<EM>(rest, lineColTracker)
                    } else {
                        throw IllegalStateException("unreachable")
                    }

                    val tag = MDFormat.Italic<FlowContent>(inner)
                    output.push(tag as MDFormat<*, ParentTagType>)
                    pos += inner.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                is MDToken.ESCAPE -> {
                    val currFmt = MDFormat.Text<FlowContent>(curr.escapedChar.toString())
                    output.push(currFmt as MDFormat<*, ParentTagType>)
                }

                is MDToken.TEXT -> {
                    if (curr.text.isBlank() && input.getOrNull(pos + 1).let { it is MDToken.UL_ITEM || it is MDToken.OL_ITEM }) {
                        // skip if this is just blank space before a list item, to avoid it getting added where it shouldn't.
                    } else {
                        if (output.top() is MDFormat.Text<*>) {
                            output.push((output.pop() as MDFormat.Text<ParentTagType>) + curr.text)
                        } else {
                            val currFmt = MDFormat.Text<FlowContent>(curr.text)
                            output.push(currFmt as MDFormat<*, ParentTagType>)
                        }
                    }
                }

                is MDToken.TRIPLE_EQUALS -> {
                    output.push(MDFormat.HorizontalRule as MDFormat<*, ParentTagType>)
                }

                is MDToken.TRIPLE_GRAVE -> {
                    val rest = input.subList(pos + 1, input.size).takeWhile { it !is MDToken.TRIPLE_GRAVE }
                    val tag = if (rest.last() is MDToken.EOF || input[pos + rest.size + 1] is MDToken.SINGLE_GRAVE) {
                            MDFormat.MultilineCode<FlowContent>(parse(rest, lineColTracker), language = curr.language)
                        } else {
                            throw IllegalStateException("unreachable")
                        }
                    output.push(tag as MDFormat<*, ParentTagType>)
                    pos += tag.inner.size + 1 // We want to eat the closing token as well. That will happen after we exit the when.
                }

                // TODO: This should be different
                is MDToken.TRIPLE_HYPHEN -> {
                    output.push(MDFormat.HorizontalRule as MDFormat<*, ParentTagType>)
                }

                is MDToken.TRIPLE_UNDERSCORE -> {
                    output.push(MDFormat.InlineLineBreak as MDFormat<*, ParentTagType>)
                }

                is MDToken.EOF -> {}
            }

            pos += 1
        }

        return output
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

}

fun main() {
    val input = File("/home/wscp/jinrui-wa-suitai-shimashita/out5.md").readText()

    val thing = createHTML(true).body {
        MarkdownTreeMaker().parse(input).forEach {
//            with(it as MDFormat<HTMLTag>) {
//                toHtmlCtor().invoke(this@body)
//            }
        }
    }
}

fun <T: Any> T.wrapMutableList() = mutableListOf(this)

inline fun <T, reified U> Iterable<T>.findIsInstance() = this.find { it is U } as? U