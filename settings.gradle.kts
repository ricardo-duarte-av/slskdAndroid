pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "slskdAndroid"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:datastore")
include(":core:network")
include(":core:data")

include(":feature:search:api")
include(":feature:search:impl")
include(":feature:connection:api")
include(":feature:connection:impl")
include(":feature:downloads:api")
include(":feature:downloads:impl")
include(":feature:uploads:api")
include(":feature:uploads:impl")
include(":feature:rooms:api")
include(":feature:rooms:impl")
include(":feature:chat:api")
include(":feature:chat:impl")
include(":feature:users:api")
include(":feature:users:impl")
include(":feature:browse:api")
include(":feature:browse:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
