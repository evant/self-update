package me.tatarka.android.selfupdate

import me.tatarka.android.selfupdate.manifest.Manifest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class ArtifactState(
    val url: HttpUrl,
    val bytesWritten: BytesWritten = BytesWritten.Zero,
    val size: Long = 0L,
)

internal fun artifactStates(
    manifestUrl: HttpUrl,
    artifacts: List<Manifest.Artifact>,
    metadata: Map<String, ArtifactMetadata> = emptyMap(),
): Map<String, ArtifactState> {
    return artifacts.associate { artifact ->
        val name = nameArtifact(artifact)
        val path = artifact.path
        val url = manifestUrl.relativePath(path)
        val artifactMetadata = metadata[name]
        name to ArtifactState(
            url = url,
            bytesWritten = artifactMetadata?.bytesWritten ?: BytesWritten.Zero,
            size = artifactMetadata?.size ?: -1L
        )
    }
}

internal fun canReuseSession(
    versionCode: Long,
    newArtifactNames: Set<String>,
    metadata: ReleaseMetadata,
): Boolean {
    // A different version can never be reused
    if (versionCode != metadata.versionCode) return false
    // artifacts can't be deleted from a session so it can't be reused if artifacts have been
    // removed or changed
    if (newArtifactNames.size < metadata.artifacts.size) return false
    val currentNames = metadata.artifacts.keys
    if (currentNames.any { name -> name !in newArtifactNames }) return false
    return true
}


private fun nameArtifact(artifact: Manifest.Artifact): String {
    val checksum = checksum(artifact)
    // name should be unique and stable
    return buildString {
        val baseName = artifact.path.removeSuffix("/")
            .substringAfterLast('/')
        append(baseName)
        if (checksum != null) {
            append('-')
            append(checksum)
        } else {
            if (artifact.abi != null) {
                append('-')
                append(artifact.abi)
            }
            if (artifact.density != null) {
                append('-')
                append(artifact.density)
            }
            if (artifact.language != null) {
                append('-')
                append(artifact.language)
            }
        }
    }
}

private fun checksum(artifact: Manifest.Artifact): String? {
    val checksums = artifact.checksums
    if (checksums.isNullOrEmpty()) return null
    // sort to ensure consist ordering
    return checksums.min().substringAfterLast(":")
}

private fun HttpUrl.relativePath(path: String): HttpUrl {
    return path.toHttpUrlOrNull() ?: run {
        if (path.startsWith("/")) {
            HttpUrl.Builder()
                .scheme(scheme)
                .port(port)
                .host(host)
                .addPathSegments(path.removePrefix("/"))
                .build()
        } else {
            newBuilder()
                .apply {
                    if (pathSize > 0) {
                        removePathSegment(pathSize - 1)
                    }
                }
                .addPathSegments(path)
                .build()
        }
    }
}


