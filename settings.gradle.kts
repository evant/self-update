pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "self-update-project"
include(":self-update-core")
include(":sample")
