// :web —— 本地 HTTP 服务 + 静态 WebUI（2.2.2 起）
//
// 为什么单独成模块：它只依赖 :shared（纯 JVM），桌面端按需起服务，
// Android 端后续若也要起（需要前台服务）同样能复用。
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    // wasm 前端与 API 分端口，需要 CORS
    implementation("io.ktor:ktor-server-cors:3.0.3")
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}
