import com.android.build.gradle.internal.tasks.BaseTask
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@OptIn(ExperimentalSerializationApi::class)
abstract class MergeSelfUpdateManifests : BaseTask() {

    @get:InputFiles
    abstract val manifests: ConfigurableFileCollection
    
    @get:Input
    @get:Optional
    abstract val variants: SetProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        val inputManifests: Map<String, Manifest> = manifests.asIterable()
            .filter { it.exists() }
            .associate { manifest ->
                manifest.parentFile.name to manifest.inputStream().buffered().use {
                    ManifestJson.decodeFromStream(it)
                }
            }
        val allVariants = variants.getOrElse(emptySet())
        val releases = ArrayList<Manifest.Release>()
        for ((variant, manifest) in inputManifests) {
            for (release in manifest.releases) {
                val tags = if (variant in allVariants) release.tags + variant else release.tags
                val existingRelease = releases.indexOfFirst {
                    it.version_code == release.version_code && it.tags == tags
                }
                val variantRelease = if (release.tags != tags) {
                    release.copy(tags = tags)
                } else {
                    release
                }
                if (existingRelease != -1) {
                    releases[existingRelease] = variantRelease
                } else {
                    releases.add(variantRelease)
                }
            }
        }
        releases.sortByDescending { it.version_code }
        output.get().asFile.outputStream().buffered().use {
            ManifestJson.encodeToStream(Manifest(releases = releases), it)
        }
    }
}