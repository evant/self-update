import org.gradle.api.publish.maven.MavenPom

fun MavenPom.mavenCentralPom() {
    name.set("self-update")
    description.set(" A system for self-updating your android app.")
    url.set("https://github.com/evant/self-update")
    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    developers {
        developer {
            id.set("evant")
            name.set("Eva Tatarka")
        }
    }
    scm {
        connection.set("https://github.com/evant/self-update.git")
        developerConnection.set("https://github.com/evant/self-update.git")
        url.set("https://github.com/evant/self-update")
    }
}
