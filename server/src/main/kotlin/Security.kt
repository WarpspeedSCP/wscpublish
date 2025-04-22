package com.example

import com.codahale.metrics.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.css.*
import kotlinx.html.*

fun Application.configureSecurity() {
    install(CSRF) {
        // tests Origin is an expected value
        allowOrigin("http://localhost:8080")
    
        // tests Origin matches Host header
        originMatchesHost()
    
        // custom header checks
        checkHeader("X-CSRF-Token")
    }
}
