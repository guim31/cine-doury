pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "cine-doury"

include(":core:model")
include(":core:source")
include(":core:designsystem")
include(":core:data")
include(":core:ui")
include(":core:playback")
include(":core:update")
include(":feature:auth")
include(":feature:home")
include(":feature:library")
include(":feature:settings")
include(":feature:details")
include(":feature:player")
include(":feature:manage")
include(":feature:search")
include(":feature:update")
include(":source:jellyfin")
include(":source:seerr")
include(":app:tv")
