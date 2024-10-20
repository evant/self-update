package me.tatarka.android.selfupdate

import android.os.PersistableBundle
import kotlin.math.absoluteValue

internal class ReleaseMetadata(val versionCode: Long) {
    val artifacts = mutableMapOf<String, ArtifactMetadata>()
}

internal class ArtifactMetadata {
    var size: Long = 0
    var bytesWritten: BytesWritten = BytesWritten.Zero
}

internal fun ReleaseMetadata.update(response: DownloadResponse) {
    for (artifactResponse in response.artifactResponses) {
        artifacts.getOrPut(artifactResponse.name) {
            ArtifactMetadata()
        }.apply {
            bytesWritten = artifactResponse.bytesWritten
            size = artifactResponse.size
        }
    }
}

@JvmInline
internal value class BytesWritten(val rawValue: Long) : Comparable<Long> {
    val bytes: Long get() = rawValue.absoluteValue
    val complete: Boolean get() = rawValue < 0

    companion object {
        val Zero = BytesWritten(0)
        fun complete(bytes: Long): BytesWritten = BytesWritten(-bytes.absoluteValue)
    }

    operator fun plus(bytes: Long): BytesWritten {
        require(!complete) { "cannot add bytes to completed" }
        return BytesWritten(rawValue + bytes)
    }

    override fun compareTo(other: Long): Int {
        return bytes.compareTo(other)
    }

    override fun toString(): String {
        return bytes.toString()
    }
}

private const val KeyVersionCode = "v"
private const val KeyArtifacts = "a"
private const val KeySize = "s"
private const val KeyBytesWritten = "b"

internal fun PersistableBundle.toReleaseMetadata(): ReleaseMetadata? {
    val versionCode = getLong(KeyVersionCode, -1)
    if (versionCode == -1L) return null
    val result = ReleaseMetadata(versionCode)
    val artifacts = getPersistableBundle(KeyArtifacts)
    if (artifacts != null) {
        for (name in artifacts.keySet().orEmpty()) {
            val artifact = artifacts.getPersistableBundle(name)
            if (artifact != null) {
                result.artifacts[name] = ArtifactMetadata().apply {
                    size = artifact.getLong(KeySize)
                    bytesWritten = BytesWritten(artifact.getLong(KeyBytesWritten))
                }
            }
        }
    }

    return result
}

internal fun ReleaseMetadata.toPersistableBundle(): PersistableBundle {
    return PersistableBundle().apply {
        putLong(KeyVersionCode, versionCode)
        if (artifacts.isNotEmpty()) {
            putPersistableBundle(KeyArtifacts, PersistableBundle().apply {
                for ((name, artifact) in artifacts) {
                    putPersistableBundle(name, PersistableBundle().apply {
                        putLong(KeySize, artifact.size)
                        putLong(KeyBytesWritten, artifact.bytesWritten.rawValue)
                    })
                }
            })
        }
    }
}