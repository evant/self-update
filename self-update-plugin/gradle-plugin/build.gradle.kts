plugins {
    alias(libs.plugins.conventions.gradle.plugin)
    alias(libs.plugins.conventions.gradle.plugin.publish)
}

dependencies {
    implementation(libs.selfupdate.manifest)
    implementation(libs.android.gradle)
    implementation(libs.bundletool)
    implementation(libs.protobuf)
    implementation(libs.kotlinx.serialization.json)
}

gradleTestKitSupport {
    withIncludedBuildProjects(":self-update-manifest:")
}

gradlePlugin {
    plugins {
        create("selfUpdate") {
            id = group.toString()
            implementationClass = "me.tatarka.android.selfupdate.gradle.SelfUpdatePlugin"
        }
    }
}