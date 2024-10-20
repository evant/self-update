import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val libs = the<LibrariesForLibs>()

val tag = (findProperty("tag") as? String)?.takeIf { it.isNotEmpty()  }

group = "me.tatarka.android.selfupdate"
version = if (tag != null) {
    val libVersion = libs.versions.version.get()
    if (tag != libVersion) {
        throw InvalidUserDataException("tag: $tag does not match version $libVersion")
    }
    tag
} else {
    libs.versions.version.get() + "-SNAPSHOT"
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget = libs.versions.jvmTarget.map(JvmTarget::fromTarget)
}

tasks.withType<JavaCompile>().configureEach {
    val version = libs.versions.jvmTarget.get()
    sourceCompatibility = version
    targetCompatibility = version
}
