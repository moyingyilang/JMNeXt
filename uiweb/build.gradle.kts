plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 版本必须成组：ui/foundation/runtime 用 1.12.0（它依赖 skiko 0.150.1），
// material3 的稳定版号落后，只能用同列的 alpha。skiko 运行时另有 0.150.1 的
// skiko-js-wasm-runtime 可下载 —— 这一点很关键：0.13x 之后运行时不再随 klib 发布，
// 版本对不上就会在浏览器里报符号错误。
val cmp = "1.12.0"
val material3 = "1.12.0-alpha03"
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
                implementation("org.jetbrains.compose.ui:ui:$cmp")
                implementation("org.jetbrains.compose.material3:material3:$material3")

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
