plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    // 数据层大量使用 @Serializable。缺了这个编译器插件，
    // 报错会是满屏的 "Unresolved reference 'serializer'"，而不是一句"缺少插件"。
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

// 仓库统一在 settings.gradle.kts 里声明（那里设了 FAIL_ON_PROJECT_REPOS）

/**
 * 跨平台数据层共用同一份源码。
 *
 * 桌面端自成一个 Gradle 构建（不并进 Android 的 settings），因此不能写
 * `implementation(project(":shared"))` —— 那样会让桌面构建依赖 Android 工程。
 * 直接按路径引入源码是这里的取舍：代价是 `:shared` 会被编译两次（Android 一次、桌面一次），
 * 好处是桌面构建完全不需要 Android SDK，容器里也能独立编译。
 *
 * 顺带它还是一个**验证**：能在纯 JVM 环境编译通过，才真正说明数据层与 Android 无关 ——
 * 在 Android 工程里编译是证明不了的，那里有 SDK 在类路径上。
 */
sourceSets.main {
    kotlin.srcDir("../shared/src/main/kotlin")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(compose.desktop.currentOs)
    // material3 不在 currentOs 里，要单独加（Kotlin/Compose 的实际报错就是第 3 行 unresolved）
    implementation(compose.material3)

    // 数据层的依赖。版本与 Android 侧（gradle/libs.versions.toml）保持一致，
    // 否则同一个仓库里的两份构建会悄悄跑在不同版本上。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

// 说明：2.1.8 桌面端自成一个 Gradle 构建（不并进 Android 的 settings），
// 这样构建桌面版完全不需要 Android SDK，容器里也就能独立编译。
compose.desktop {
    application {
        // 必须是全限定名：之前这里写的是裸 MainKt，而仓库里同时存在一个默认包的
        // 旧验证文件（src/main/kotlin/Main.kt），于是启动的永远是那个 hello-world。
        // 那个文件已删除，这里也改成全限定名，避免同类问题再发生。
        mainClass = "com.jmnext.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
            )
            packageName = "jmnext"  // deb/rpm 对包名字符有限制，大写与下划线不合法
            packageVersion = "2.1.9"
            description = "JMNeXt 桌面版"
            vendor = "moyingyilang"
        }
    }
}

/**
 * 连通性冒烟：登录并拉一次首页列表。
 *
 * 单独做一个 JavaExec 而不是复用 compose 的 run 任务：它没有界面，
 * 因此能在无显示环境（容器里）直接跑，这是"数据层在真实网络下能跑通"的最短验证路径。
 * 凭据从环境变量读，不经过命令行参数。
 */
tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "桌面端连通性冒烟（无界面）：JM_USER/JM_PASS gradle smoke"
    mainClass.set("com.jmnext.desktop.SmokeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// ── 单体（fat jar）────────────────────────────────────────────────────────────
// 用户偏好"单体"：一个文件、java -jar 就能跑。用 Gradle 自带的 Jar 任务合并即可，
// 不需要 shadow 插件。
//
// 关键点：Compose/Skiko 的平台原生库是**按宿主解析**的，所以交叉时必须
// 从依赖里剔除所有 skiko-awt-runtime-*，再按 -Ptarget= 显式加入目标平台那一份，
// 否则打出来的 jar 会带着宿主的原生库，到目标机上无法加载。
val targetPlatform = (findProperty("target") as String?) ?: "host"

val skikoForTarget = mapOf(
    "windows-arm64" to "org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:0.150.1",
    "linux-arm64" to "org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.150.1",
    "linux-x64" to "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.150.1",
)

val targetNative by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    skikoForTarget[targetPlatform]?.let { targetNative(it) }
}

tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "把所有依赖合成一个可 java -jar 运行的单体 jar（-Ptarget=windows-arm64 交叉）"
    archiveBaseName.set("jmnext")
    archiveClassifier.set(targetPlatform)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.jmnext.desktop.MainKt"
        attributes["Implementation-Version"] = "2.1.9"
    }
    from(sourceSets.main.get().output)
    from({
        // 宿主那份 skiko-awt-runtime-* 一律剔除；再用 -Ptarget 指定的那份补回来。
        // 注意：补回来的那份名字也是 skiko-awt-runtime-*，所以不能放在同一个过滤里，
        // 否则会被自己刚加的过滤器剔掉（这里踩过一次，打出来的 jar 一个原生库都没有）。
        val hostDeps = configurations.runtimeClasspath.get().files
            .filter { it.name.endsWith(".jar") }
            .filterNot { it.name.startsWith("skiko-awt-runtime") }
        val targetNatives = if (targetPlatform == "host") {
            emptyList()
        } else {
            targetNative.files.filter { it.name.startsWith("skiko-awt-runtime") }
        }
        (hostDeps + targetNatives).map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
