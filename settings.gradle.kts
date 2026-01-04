pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")   // ⬅️ 一定要有这一行
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ⬇⬇⬇ 这一行非常关键：JitPack 仓库
        maven(url = uri("https://jitpack.io"))
    }
}

rootProject.name = "cgmdemo"
include(":app")
