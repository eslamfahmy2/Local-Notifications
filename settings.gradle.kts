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
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LocalNotifications"

include(":core:domain")
include(":core:data")
include(":core:notifications")
include(":core:mvi")
include(":core:testing")
include(":core:device-testing")
include(":feature:list")
include(":feature:detail")
include(":app-views")
include(":app-compose")
