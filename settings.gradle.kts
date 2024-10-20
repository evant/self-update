pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
    includeBuild("self-update-plugin")
}

dependencyResolutionManagement {
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "self-update-project"
includeBuild("self-update-plugin")
includeBuild("self-update-manifest") {
    dependencySubstitution {
        substitute(module("me.tatarka.android.selfupdate:manifest")).using(project(":"))
    }
}
include(":self-update-core")
include(":sample")