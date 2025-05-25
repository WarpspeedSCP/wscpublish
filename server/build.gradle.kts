
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.withType<ProcessResources> {
    val wasmOutput = file("../web/build/dist/wasmJs/productionExecutable")
    if (wasmOutput.exists()) {
        inputs.dir(wasmOutput)
    }

    from("../web/build/dist/wasmJs/productionExecutable") {
        into("web")
        include("**/*")
    }
    duplicatesStrategy = DuplicatesStrategy.WARN
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.html)
    implementation(libs.kotlin.css)
    implementation(libs.kotlinwind.css)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.csrf)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation()
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
