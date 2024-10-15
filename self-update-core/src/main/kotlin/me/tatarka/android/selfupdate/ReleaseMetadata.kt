package me.tatarka.android.selfupdate

import android.os.PersistableBundle
import kotlin.math.absoluteValue

internal class ReleaseMetadata(val versionCode: Long) {
    val artifacts = mutableMapOf<String, ArtifactMetadata>()

    internal class ArtifactMetadata {
        var size: Long = 0
        var bytesWritten: Long = 0
        var checksum: String? = null

        fun isDownloadComplete(): Boolean {
            return bytesWritten < 0
        }

        fun markDownloadComplete(sizeBytes: Long) {
            bytesWritten = -sizeBytes.absoluteValue
        }
    }
}

private const val VersionCode = "v"
private const val Artifacts = "a"
private const val Size = "s"
private const val BytesWritten = "b"
private const val Checksum = "c"

internal fun PersistableBundle.toReleaseMetadata(): ReleaseMetadata? {
    val versionCode = getLong(VersionCode, -1)
    if (versionCode == -1L) return null
    val result = ReleaseMetadata(versionCode)
    val artifacts = getPersistableBundle(Artifacts)
    if (artifacts != null) {
        for (name in artifacts.keySet().orEmpty()) {
            val artifact = artifacts.getPersistableBundle(name)
            if (artifact != null) {
                result.artifacts[name] = ReleaseMetadata.ArtifactMetadata().apply {
                    size = artifact.getLong(Size)
                    bytesWritten = artifact.getLong(BytesWritten)
                    checksum = artifact.getString(Checksum)
                }
            }
        }
    }

    return result
}

internal fun ReleaseMetadata.toPersistableBundle(): PersistableBundle {
    return PersistableBundle().apply {
        putLong(VersionCode, versionCode)
        if (artifacts.isNotEmpty()) {
            putPersistableBundle(Artifacts, PersistableBundle().apply {
                for ((name, artifact) in artifacts) {
                    putPersistableBundle(name, PersistableBundle().apply {
                        putLong(Size, artifact.size)
                        putLong(BytesWritten, artifact.bytesWritten)
                        putString(Checksum, artifact.checksum)
                    })
                }
            })
        }
    }
}