plugins {
    id("conventions.base")
    `maven-publish`
    signing
}

publishing {
    publications {
        register<MavenPublication>("release") {
            pom {
                mavenCentralPom()
            }
        }
    }
}

signing {
    setRequired {
        findProperty("signing.keyId") != null
    }

    publishing.publications.all {
        // skip signing testkit
        if (!name.contains("testKit")) {
            sign(this)
        }
    }
}