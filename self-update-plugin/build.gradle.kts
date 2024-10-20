buildscript {
    dependencies {
        with(libs.kotlin.gradle.get()) {
            classpath("$group:$name:$embeddedKotlinVersion")
        }
    }
}

plugins {
    alias(libs.plugins.conventions.root)
}

tasks.register("publish")
tasks.register("publishToMavenLocal")