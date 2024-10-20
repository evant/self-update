dependencyResolutionManagement {
    versionCatalogs {
        val libs by registering {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
