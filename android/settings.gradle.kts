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
        maven {
            url = uri(rootDir.resolve("local-maven"))
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AiBookAndroid"

include(":app")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:reader")
