package dev.wscp.data

data class RssContext(
    val path: String,
    val title: String,
)

data class PageContext(
    val route: String,
    val title: String = "",
    val description: String = "",
    val keywords: List<String> = listOf(),
    val rss: RssContext? = null,

)
