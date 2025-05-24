package dev.wscp.data

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class ArticleContextKtTest {

    @Test
    fun testMdToks() {
        "# [abc](def)".mdTokens()
        assertEquals(
            listOf(
                MDToken.HTag.H1::class,
                MDToken.TEXT::class,
                MDToken.TEXT::class,
                MDToken.TEXT::class,
                MDToken.TEXT::class,
                MDToken.EOF::class,
            ),
            "# Hello World".mdTokens().map { it::class }
        )
        assertEquals(listOf("*", " \t", "**", "abc", " ", "d", "**"), "**abc*\n* \t**abc d**".mdTokens())
        assertEquals(listOf("***", "abc", " ", "de", "**", " ", "f", "*"), "***abc de** f*".mdTokens())
    }

    @Test
    fun parse() {
        val parser = MarkdownTreeMaker()

        val input = """
<p>
- a
- b
  <hr/>
 - *c
   \*** e
   ***
 * ***abc** de*
</p>
- d
""".trimIndent()

        val output = MDTokeniser(input).output
        val outut = parser.parse(output, LineColTracker(input))
    }
}