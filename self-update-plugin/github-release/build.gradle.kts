plugins {
    alias(libs.plugins.conventions.gradle.plugin)
    alias(libs.plugins.conventions.gradle.plugin.publish)
    embeddedKotlin("plugin.serialization")
}

dependencies {
    implementation(libs.selfupdate.manifest)
    implementation(libs.android.gradle)
    implementation(libs.kotlinx.serialization.json)
    api(project(":gradle-plugin"))
}

gradleTestKitSupport {
    withIncludedBuildProjects(":self-update-manifest:")
}

gradlePlugin {
    plugins {
        create("githubRelease") {
            id = "${group}.github-release"
            implementationClass = "me.tatarka.android.selfupdate.gradle.GithubReleasePlugin"
        }
    }
}