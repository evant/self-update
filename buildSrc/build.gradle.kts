import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    embeddedKotlin("plugin.serialization")
}

dependencies {
    implementation("com.android.tools.build:gradle:8.6.1")
    implementation("com.android.tools.build:bundletool:1.17.1")
    implementation("com.google.protobuf:protobuf-java:3.22.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api(kotlin("gradle-plugin:1.9.20"))
}

java {
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

gradlePlugin {
    plugins {
        create("selfUpdate") {
            id = "me.tatarka.selfupdate"
            implementationClass = "SelfUpdatePlugin"
        }
    }
}