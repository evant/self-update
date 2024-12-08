pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("../build-logic")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":gradle-plugin")
include(":github-release-plugin")
includeBuild("../self-update-manifest") {
    dependencySubstitution {
        substitute(module("me.tatarka.android.selfupdate:self-update-manifest")).using(project(":"))
    }
}
