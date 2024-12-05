import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.gradle.kotlin.kotlin-dsl")
    `java-gradle-plugin`
    id("com.autonomousapps.testkit")
}

val libs = the<LibrariesForLibs>()

dependencies {
    functionalTestImplementation(libs.bundles.gradle.test)
    functionalTestRuntimeOnly(libs.junit.platform.launcher)
    api(libs.kotlin.gradle)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

// testkit doesn't see the version of the included build dependency so set it explicitly
dependencies {
    constraints {
        implementation(libs.selfupdate.manifest) {
            version { require(project.version.toString()) }
        }
    }
}