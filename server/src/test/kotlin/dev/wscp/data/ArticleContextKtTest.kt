package dev.wscp.data

import dev.wscp.markdown.CustomHtmlAppender
import dev.wscp.markdown.LineColTracker
import dev.wscp.markdown.MDToken
import dev.wscp.markdown.MDTokeniser
import dev.wscp.markdown.mdTokens
import kotlinx.html.ARTICLE
import kotlinx.html.article
import kotlinx.html.consumers.delayed
import kotlinx.html.consumers.onFinalizeMap
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

        var input = """
<div style1="3" onClick="alert('a' > 'b')">
- [a](<https://abc.def/(abc)>)
- b
  <hr/>
 - *c
   \*** e
   ***
 * ***abc\
   def** de*
</div>
- > d
  > e
> f
>> g
>>> h
>> i
  - j
""".trimIndent()

//        input = File("/home/wscp/wscp_dev/content/posts/tls/jintai/vol2/chapter2/v2c2p1.md").readText()
        val output = MDTokeniser(input).output
        val outut = parser.parse<ARTICLE>(output, LineColTracker(input))
        CustomHtmlAppender(
            StringBuilder(32768),
            true,
            false
        ).onFinalizeMap { sb, _ -> sb.toString() }
            .delayed()
            .article {
            for (i in outut) {
                i.toHtml(this)
            }
        }.let {
            assertEquals("""<article>
  <div style1="3" onClick="alert('a' > 'b')">
    <ul>
      <li> <a href="https://abc.def/(abc)">a</a></li>
      <li> b  
        <hr>
        <ul>
          <li> <em>c   *<strong> e   </strong></em></li>
          <li> <em><strong>abc<br>   def</strong> de</em></li>
        </ul>
      </li>
    </ul>
  </div>
  <ul>
    <li>   
      <blockquote>
        <p>def
          <blockquote>
            <p>g
              <blockquote>
                <p>h</p>
              </blockquote>
i j</p>
          </blockquote>
        </p>
      </blockquote>
    </li>
  </ul>
</article>
""", it)
        }
    }
}