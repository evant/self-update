import com.android.build.gradle.internal.tasks.BaseTask
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.Serializable

class Version(val name: String, val code: Long) : Serializable

@CacheableTask
@OptIn(ExperimentalSerializationApi::class)
abstract class GenerateSelfUpdateManifest : BaseTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifacts: DirectoryProperty

    @get:Input
    abstract val version: Property<Version>

    @get:Input
    @get:Optional
    abstract val tags: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val notes: Property<String>

    @get:Input
    @get:Optional
    abstract val artifactSuffix: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        val version = version.get()
        val apkSplitsPath = artifacts.get().asFile
        val artifacts = parseArtifactMetadata(apkSplitsPath.resolve("toc.pb")) { path ->
            path.removeSurrounding(prefix = "splits/", suffix = ".apk") + 
                    artifactSuffix.getOrElse("") + ".apk"
        }
        val manifestPath = output.get().asFile
        manifestPath.outputStream().buffered().use {
            ManifestJson.encodeToStream(
                Manifest(
                    releases = listOf(
                        Manifest.Release(
                            version_name = version.name,
                            version_code = version.code,
                            tags = tags.getOrElse(emptySet()),
                            notes = notes.orNull,
                            artifacts = artifacts
                        )
                    )
                ),
                it
            )
        }
    }
}
