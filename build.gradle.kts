plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.conventions.root)
}

// forward the following tasks to included builds:
listOf(
    "publish",
    "publishToMavenLocal",
).forEach { task ->
    tasks.register(task) {
        dependsOn(gradle.includedBuild("self-update-manifest").task(":$task"))
        dependsOn(gradle.includedBuild("self-update-plugin").task(":$task"))
    }
}

