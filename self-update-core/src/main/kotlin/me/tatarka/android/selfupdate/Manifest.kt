@file:Suppress("PropertyName")

package me.tatarka.android.selfupdate

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

@Serializable
internal class Manifest(val releases: List<Release>) {
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

    @Serializable
    class Release(
        val version_name: String,
        val version_code: Long,
        val artifacts: List<Artifact>,
        val minSdk: Int,
        val maxSdk: Int? = null,
        val tags: Set<String> = emptySet(),
        val updater: Updater? = null,
    )

    @Serializable
    class Artifact(
        val path: String,
        val density: String? = null,
        val abi: String? = null,
        val language: String? = null,
    )

    @Serializable
    class Updater(
        val version: Long,
        val feature_version: Long,
    )
}