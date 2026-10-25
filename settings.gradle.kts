pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Kotlin/Wasm 需要 Node 运行时；官方的 node 分发不在 Maven Central，
        // 而在 nodejs.org/dist。PREFER_SETTINGS 模式下必须在这里显式声明。
        ivy("https://nodejs.org/dist") {
            patternLayout {
                artifact("/dist/v[revision]/[artifact]-v[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
        }
    }
}

rootProject.name = "JMNeXt"

include(":app")
include(":shared")
include(":web")
include(":uiweb")
