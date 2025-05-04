package dev.wscp.templates

import dev.wscp.Routes
import dev.wscp.data.ArticleContext
import dev.wscp.data.PageContext
import io.github.allangomes.kotlinwind.css.kw
import io.ktor.server.html.*
import kotlinx.css.noscript
import kotlinx.html.*

//<!-- Basic stuff -->
//<meta charset="utf-8">
//<meta name="HandheldFriendly" content="True">
//<meta name="viewport" content="width=device-width, initial-scale=1.0">
//<meta name="referrer" content="no-referrer-when-downgrade">


fun HTML.pageHead(
    context: PageContext,
) {
    head {
        meta {
            charset = "utf-8"
        }
        meta("HandheldFriendly", "True")
        meta("viewport", "width=device-width, initial-scale=1.0")
        meta("referrer", "no-referrer-when-downgrade")
        title {
            if (context.route == Routes.HOME) +"WSCP's Blog"
            else +"WSCP's Blog - ${context.title}"
        }
        meta("description", context.description)

        if (context.keywords.isNotEmpty())
            meta("keywords", content = context.keywords.joinToString(","))

        if (context.rss != null) {
            link {
                rel = "alternate"
                type = "application/rss+xml"
                href = context.rss.path
                title = context.rss.title
            }
        }
        style {
            unsafe {
                raw("body { visibility: hidden; opacity: 0; }")
            }
        }
        style {
            noScript {
                unsafe {
                    raw("body { visibility: visible; opacity: 1; }")
                }
            }
        }

        script(type = ScriptType.textJavaScript) {
            attributes["data-goatcounter"] = "https://wscp.goatcounter.com/count"
            async = true
            src = "//gc.zgo.at/count.js"
        }

        script(type = ScriptType.textJavaScript) {
            src = "https://code.jquery.com/jquery-3.7.1.min.js"
            integrity = "sha256-kmHvs0B+OpCW5GVHUNjv9rOmY0IvSIRcf7zGUDTDQM8="
            crossorigin = ScriptCrossorigin.anonymous
        }

        script(type = ScriptType.textJavaScript) {
            unsafe {
                raw("$('html').toggleClass('js');")
            }
        }
    }
}

fun BODY.createArticle(context: ArticleContext) {

}

//<title>{{ if and (.Title) (not .IsHome) }}{{ .Title }} - {{ end }}{{ .Site.Title }}</title>
//<meta name="description" content="{{ with .Description }}{{ . }}{{ else }}{{if .IsPage}}{{ .Summary }}{{ else }}{{ with .Site.Params.description }}{{ . }}{{ end }}{{ end }}{{ end -}}">
//
//{{ with .Keywords }}
//<meta name="keywords" content="{{ range $i, $e := . }}{{ if $i }},{{ end }}{{ $e }}{{ end }}">
//{{ end }}
//
//{{ if and (.IsPage) (eq .Params.hidden true)}}
//<meta name="robots" content="noindex" />
//{{ end }}
//
//{{ with .OutputFormats.Get "rss" -}}
//{{ printf `<link rel="%s" type="%s" href="%s" title="%s" />` .Rel .MediaType.Type .Permalink $.Site.Title | safeHTML }}
//{{ end -}}
//
//{{ partial "favicons.html" . }}
//
//<style>
//body {
//    visibility: hidden;
//    opacity: 0;
//}
//</style>
//
//<noscript>
//<style>
//body {
//    visibility: visible;
//    opacity: 1;
//}
//</style>
//</noscript>
//
//{{ partial "resource.html" (dict "context" . "type" "css" "filename" "css/main.css") }}
//
//{{ if .Site.Params.copyCodeButton | default true }}
//{{ partial "resource.html" (dict "context" . "type" "js" "filename" "js/copy-code.js") }}
//{{ end }}
//
//{{ range .Site.Params.customJS -}}
//{{ partial "resource.html" (dict "context" $ "type" "js" "filename" . ) }}
//{{- end }}
//
//{{/*  {{ template "_internal/opengraph.html" . }}  */}}
//{{/*  {{ template "_internal/twitter_cards.html" . }}  */}}
//
//{{ if isset .Site.Params "webmentions" }}
//{{ if isset .Site.Params.webmentions "login"  }}
//<link rel="webmention" href="https://webmention.io/{{.Site.Params.webmentions.login}}/webmention" />
//{{ if eq .Site.Params.webmentions.pingback true  }}
//<link rel="pingback" href="https://webmention.io/{{.Site.Params.webmentions.login}}/xmlrpc" />
//{{ end }}
//{{ end }}
//{{ if isset .Site.Params.webmentions "url"  }}
//<link rel="webmention" href="{{.Site.Params.webmentions.url}}" />
//{{ end }}
//{{ end }}
//
//<!-- Article tags -->
//<!-- <meta property="article:published_time" content="">
//<meta property="article:modified_time" content="">
//<meta property="article:tag" content="">
//<meta property="article:publisher" content="https://www.facebook.com/XXX"> -->
//
//<script data-goatcounter="https://wscp.goatcounter.com/count"
//async src="//gc.zgo.at/count.js"></script>
//
//{{ partial "head-extra.html" . }}
