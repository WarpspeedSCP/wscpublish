package dev.wscp.data

import kotlinx.css.times
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class ArticleContextKtTest {

    @Test
    fun testMdToks() {
        "# [abc](def)".mdTokens()
        assertEquals(listOf("#", " ", "Hello", " ", "World"), "# Hello World".mdTokens())
        assertEquals(listOf("*", " \t", "**", "abc", " ", "d", "**"), "* \t**abc d**".mdTokens())
        assertEquals(listOf("***", "abc", " ", "de", "**", " ", "f", "*"), "***abc de** f*".mdTokens())
    }

    @Test
    fun parse() {
        val parser = MarkdownTreeMaker()

        val input = """
- a
- b
 - c
- d
""".trimIndent()

        val output = MDTokeniser(input).output
        val outut = parser.parse(output, LineColTracker(input))
    }
}