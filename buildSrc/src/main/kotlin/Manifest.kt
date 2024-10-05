import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val ManifestJson = Json { ignoreUnknownKeys = true }

@Serializable
internal class Manifest(val releases: List<Release>) {
    
    @Serializable
    data class Release(
        val version_name: String,
        val version_code: Long,
        val tags: Set<String> = emptySet(),
        val notes: String? = null,
        val artifacts: List<Artifact>,
    )
    
    @Serializable
    class Artifact(
        val path: String,
        val abi: String? = null,
        val density: String? = null,
        val language: String? = null,
    )
}