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
        val release = named<MavenPublication>("release")
        release.configure {
            from(components["java"])
        }
        afterEvaluate {
            gradlePlugin.plugins.forEach { plugin ->
                register<MavenPublication>("releasePluginMarkerMaven").configure {
                    val pluginArtifact = release.get()
                    groupId = plugin.id
                    artifactId = "${plugin.id}.gradle.plugin"
                    version = pluginArtifact.version
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
                            groupId.textContent = pluginArtifact.groupId
                            val artifactId = dependency.appendChild(document.createElement("artifactId"))
                            artifactId.textContent = pluginArtifact.artifactId
                            val version = dependency.appendChild(document.createElement("version"))
                            version.textContent = pluginArtifact.version
                        }
                    }
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
