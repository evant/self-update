package me.tatarka.android.selfupdate

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class ArtifactStateTest {
    @Test
    fun canReuseSession_returns_true_if_no_artifacts_have_been_downloaded() {
        val versionCode = 1L
        val artifactNames = setOf("base")
        val metadata = ReleaseMetadata(versionCode = 1L)

        assertThat(canReuseSession(versionCode, artifactNames, metadata)).isTrue()
    }

    @Test
    fun canReuseSession_returns_true_if_some_artifacts_have_been_downloaded() {
        val versionCode = 1L
        val artifactNames = setOf("base", "split")
        val metadata = ReleaseMetadata(versionCode = 1L)
        metadata.artifacts["base"] = ArtifactMetadata().apply {
            bytesWritten = BytesWritten.complete(1)
        }

        assertThat(canReuseSession(versionCode, artifactNames, metadata)).isTrue()
    }

    @Test
    fun canReuseSession_returns_true_if_all_artifacts_have_been_downloaded() {
        val versionCode = 1L
        val artifactNames = setOf("base", "split")
        val metadata = ReleaseMetadata(versionCode = 1L)
        metadata.artifacts["base"] = ArtifactMetadata().apply {
            bytesWritten = BytesWritten.complete(1)
        }
        metadata.artifacts["split"] = ArtifactMetadata().apply {
            bytesWritten = BytesWritten.complete(1)
        }

        assertThat(canReuseSession(versionCode, artifactNames, metadata)).isTrue()
    }

    @Test
    fun canReuseSession_returns_false_if_version_changed() {
        val versionCode = 1L
        val artifactNames = setOf("base")
        val metadata = ReleaseMetadata(versionCode = 2L)

        assertThat(canReuseSession(versionCode, artifactNames, metadata)).isFalse()
    }

    @Test
    fun canReuseSession_returns_false_if_artifacts_are_removed() {
        val versionCode = 1L
        val artifactNames = setOf("base")
        val metadata = ReleaseMetadata(versionCode = 1L)
        metadata.artifacts["base"] = ArtifactMetadata().apply {
            bytesWritten = BytesWritten.complete(1)
        }
        metadata.artifacts["split"] = ArtifactMetadata()

        assertThat(canReuseSession(versionCode, artifactNames, metadata)).isFalse()
    }

    @Test
    fun canReuseSession_returns_false_if_artifacts_change() {
        val versionCode = 1L
        val artifactNames = setOf("base", "split2")
        val metadata = ReleaseMetadata(versionCode = 1L)
        metadata.artifacts["base"] = ArtifactMetadata().apply {
            bytesWritten = BytesWritten.complete(1)
        }
        metadata.artifacts["split1"] = ArtifactMetadata()

        assertThat(canReuseSession(versionCode, artifactNames, metadata)).isFalse()
    }
}