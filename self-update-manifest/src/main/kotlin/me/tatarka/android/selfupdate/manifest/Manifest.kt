@file:Suppress("PropertyName")

package me.tatarka.android.selfupdate.manifest

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

@Serializable
class Manifest(val releases: List<Release>) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(jsonString: String): Manifest {
            return json.decodeFromString(jsonString)
        }

        @ExperimentalSerializationApi
        fun parse(jsonStream: InputStream): Manifest {
            return json.decodeFromStream(jsonStream)
        }
    }

    fun copy(releases: List<Release>): Manifest {
        return Manifest(
            releases = releases,
        )
    }

    @Serializable
    class Release(
        val version_name: String,
        val version_code: Long,
        val tags: Set<String> = emptySet(),
        val notes: String? = null,
        val minSdk: Int? = null,
        val maxSdk: Int? = null,
        val meta: Meta? = null,
        val artifacts: List<Artifact> = emptyList(),
    ) {
        fun copy(
            tags: Set<String> = this.tags,
            artifacts: List<Artifact> = this.artifacts,
        ): Release = Release(
            version_name = version_name,
            version_code = version_code,
            tags = tags,
            notes = notes,
            minSdk = minSdk,
            maxSdk = maxSdk,
            meta = meta,
            artifacts = artifacts
        )
    }

    @Serializable
    class Artifact(
        val path: String,
        val universal: Boolean? = null,
        val minSdk: Int? = null,
        val density: Int? = null,
        val abi: String? = null,
        val language: String? = null,
        val checksums: List<String>? = null,
    ) {
        fun copy(path: String): Artifact {
            return Artifact(
                path = path,
                universal = universal,
                minSdk = minSdk,
                density = density,
                abi = abi,
                language = language,
                checksums = checksums,
            )
        }
    }

    @Serializable
    class Meta(
        val version: Long,
        val feature_version: Long,
    )

    @ExperimentalSerializationApi
    fun write(jsonStream: OutputStream) {
        json.encodeToStream(this, jsonStream)
    }

    override fun toString(): String {
        return json.encodeToString(this)
    }
}