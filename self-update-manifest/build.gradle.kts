plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.conventions.root)
    alias(libs.plugins.conventions.publish)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        named<MavenPublication>("release").configure {
            from(components["java"])
        }
    }
}