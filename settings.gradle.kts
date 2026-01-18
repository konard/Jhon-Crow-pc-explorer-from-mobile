pluginManagement {
    repositories {
        google()
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

rootProject.name = "pc-explorer-from-mobile"

include(":app")
include(":core:common")
include(":core:data")
include(":core:domain")
include(":features:connection")
include(":features:browser")
include(":features:transfer")
include(":features:settings")
include(":shared")
