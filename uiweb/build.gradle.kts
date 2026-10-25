plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val cmp = "1.13.0-beta01"
val ktor = "3.0.3"
val serialization = "1.11.0"
val coroutines = "1.10.2"

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:$cmp")
                implementation("org.jetbrains.compose.foundation:foundation:$cmp")
                implementation("org.jetbrains.compose.material3:material3:$cmp")
                implementation("org.jetbrains.compose.ui:ui:$cmp")

                implementation("io.ktor:ktor-client-core:$ktor")
                implementation("io.ktor:ktor-client-js:$ktor")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
            }
        }
    }
}
