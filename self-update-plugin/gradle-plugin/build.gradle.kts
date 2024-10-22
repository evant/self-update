import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    embeddedKotlin("plugin.serialization")
    alias(libs.plugins.conventions.publish)
}

dependencies {
    implementation(libs.selfupdate.manifest)
    implementation(libs.android.gradle)
    implementation(libs.bundletool)
    implementation(libs.protobuf)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlin.gradle)
}

java {
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

gradlePlugin {
    plugins {
        create("selfUpdate") {
            id = group.toString()
            implementationClass = "me.tatarka.android.selfupdate.gradle.SelfUpdatePlugin"
        }
    }
    isAutomatedPublishing = false
}

val emptyJavadocJar = tasks.register<Jar>("emptyJavadocJar") {
    destinationDirectory = layout.buildDirectory.dir("libs-marker")
    archiveClassifier.set("javadoc")
}

val emptySourceJar = tasks.register<Jar>("emptySourceJar"){
    destinationDirectory = layout.buildDirectory.dir("libs-marker")
    archiveClassifier.set("source")
}

publishing {
    publications {
        named<MavenPublication>("release").configure {
            from(components["java"])
        }
        register<MavenPublication>("releasePluginMarkerMaven").configure {
            artifactId = "$groupId.gradle.plugin"
            artifact(emptyJavadocJar)
            artifact(emptySourceJar)
            pom {
                mavenCentralPom()
                withXml {
                    val root = asElement()
                    val document = root.ownerDocument
                    val dependencies = root.appendChild(document.createElement("dependencies"))
                    val dependency = dependencies.appendChild(document.createElement("dependency"))
                    val groupId = dependency.appendChild(document.createElement("groupId"))
                    groupId.textContent = this@configure.groupId
                    val artifactId = dependency.appendChild(document.createElement("artifactId"))
                    artifactId.textContent = this@configure.artifactId
                    val version = dependency.appendChild(document.createElement("version"))
                    version.textContent = this@configure.version
                }
            }
        }
    }
}

listOf(
    "publish",
    "publishToMavenLocal"
).forEach { task ->
    rootProject.tasks.named(task).configure {
        dependsOn(tasks.named(task))
    }
}