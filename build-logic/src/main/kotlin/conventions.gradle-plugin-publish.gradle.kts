plugins {
    id("conventions.publish")
    `java-gradle-plugin`
}

java {
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
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
                    groupId.textContent = project.group.toString()
                    val artifactId = dependency.appendChild(document.createElement("artifactId"))
                    artifactId.textContent = project.name
                    val version = dependency.appendChild(document.createElement("version"))
                    version.textContent = project.version.toString()
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
