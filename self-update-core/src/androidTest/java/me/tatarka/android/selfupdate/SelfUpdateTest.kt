package me.tatarka.android.selfupdate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import assertk.Assert
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import me.tatarka.android.selfupdate.compat.PackageInstallerCompat
import me.tatarka.android.selfupdate.manifest.Manifest
import okhttp3.internal.http2.ErrorCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.mockwebserver.internal.duplex.MockDuplexResponseBody
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class SelfUpdateTest {
    @get:Rule
    val webServer = MockWebServer()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val installer = PackageInstallerCompat.getInstance(context)
    private val selfUpdate = SelfUpdate(context = context)

    @After
    fun cleanup() {
        for (session in installer.mySessions) {
            installer.abandonSession(session.sessionId)
        }
    }

    @Test
    fun downloads_release() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(Manifest.Artifact("base.apk"))
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        // state is none before download
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.None)

        selfUpdate.download(release)

        // makes a request
        assertThat(webServer.takeRequest()).prop(RecordedRequest::path).isEqualTo("/base.apk")

        // state is complete
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.Complete)

        val sessionInfo = installer.mySessions.first()
        // size is set to Content-Length
        assertThat(sessionInfo.size).isEqualTo(8)
        // has progress
        assertThat(sessionInfo.progress).isBetween(0f, 1f)
        // has downloaded artifact fully
        installer.openSession(sessionInfo.sessionId).use { session ->
            assertThat(session).hasArtifactContents("base.apk")
        }
    }

    @Test
    fun skips_already_downloaded_release() = runTest {
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        selfUpdate.download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            )
        )
        webServer.takeRequest()

        selfUpdate.download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            )
        )

        // keeps existing session
        assertThat(installer.mySessions).hasSize(1)
    }

    @Test
    @FlakyTest
    fun deletes_existing_session() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(Manifest.Artifact("base.apk"))
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        selfUpdate.download(release)
        webServer.takeRequest()

        selfUpdate.delete(release)
        yield()

        // deletes existing session
        assertThat(installer.mySessions).isEmpty()
    }

    @Test
    fun resumes_non_downloaded_artifacts_after_failure() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(
                Manifest.Artifact("base.apk"),
                Manifest.Artifact("base-x86_64.apk")
            )
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        // fail the download
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "15")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
                .setBody(MockDuplexResponseBody().cancelStream(ErrorCode.INTERNAL_ERROR))
        )

        assertFailure {
            selfUpdate.download(release)
        }.isInstanceOf<IOException>()

        webServer.takeRequest()

        // should have partial download state
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.Partial)

        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base-x86_64.apk")
        )

        selfUpdate.download(release)

        // now should have full state
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.Complete)

        assertThat(webServer.takeRequest()).prop(RecordedRequest::path)
            .isEqualTo("/base-x86_64.apk")

        // has downloaded artifacts fully
        val sessionInfo = installer.mySessions.first()
        installer.openSession(sessionInfo.sessionId).use { session ->
            assertThat(session).hasArtifactContents("base.apk", "base-x86_64.apk")
        }
    }

    @Test
    fun resumes_partial_artifact_download_after_failure() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(Manifest.Artifact("base.apk"))
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .addHeader("Content-Range", "bytes 0-7/8")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setBody("base.apk")
        )

        assertFailure {
            selfUpdate.download(release)
        }.isInstanceOf<IOException>()

        webServer.takeRequest()

        // should have partial download state
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.Partial)

        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "4")
                .addHeader("Content-Range", "bytes 4-7/8")
                .setBody("base.apk")
        )

        selfUpdate.download(release)

        // now should have full state
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.Complete)

        assertThat(webServer.takeRequest()).prop(RecordedRequest::path)
            .isEqualTo("/base.apk")

        // has downloaded artifacts fully
        val sessionInfo = installer.mySessions.first()
        installer.openSession(sessionInfo.sessionId).use { session ->
            assertThat(session).hasArtifactContents("base.apk")
        }
    }

    @Test
    fun downloads_in_chunks() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(Manifest.Artifact("base.apk"))
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "4")
                .addHeader("Content-Range", "bytes 0-3/8")
                .setResponseCode(206)
                .setBody("base")
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "4")
                .addHeader("Content-Range", "bytes 4-7/8")
                .setResponseCode(206)
                .setBody(".apk")
        )
        // state is none before download
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.None)

        selfUpdate.download(release)

        // makes both requests
        assertThat(webServer.takeRequest())
            .prop(RecordedRequest::path).isEqualTo("/base.apk")
        assertThat(webServer.takeRequest())
            .prop(RecordedRequest::path).isEqualTo("/base.apk")

        // state is complete
        assertThat(selfUpdate.currentDownloadState(release))
            .isEqualTo(SelfUpdate.DownloadState.Complete)

        val sessionInfo = installer.mySessions.first()
        // size is set to Content-Length
        assertThat(sessionInfo.size).isEqualTo(8)
        // has progress
        assertThat(sessionInfo.progress).isBetween(0f, 1f)
        // has downloaded artifact fully
        installer.openSession(sessionInfo.sessionId).use { session ->
            assertThat(session).hasArtifactContents("base.apk")
        }
    }

    @Test
    fun adding_artifact_since_last_download_downloads_new_artifact() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(Manifest.Artifact("base.apk"))
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        selfUpdate.download(release)
        webServer.takeRequest()

        val newRelease = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(
                Manifest.Artifact("base.apk"),
                Manifest.Artifact("base-x86_64.apk")
            )
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "15")
                .setBody("base-x86_64.apk")
        )

        selfUpdate.download(newRelease)

        assertThat(webServer.takeRequest()).prop(RecordedRequest::path)
            .isEqualTo("/base-x86_64.apk")

        // has downloaded artifacts fully
        val sessionInfo = installer.mySessions.first()
        installer.openSession(sessionInfo.sessionId).use { session ->
            assertThat(session).hasArtifactContents("base.apk", "base-x86_64.apk")
        }
    }

    @Test
    fun removing_artifact_since_last_download_triggers_full_download() = runTest {
        val release = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(
                Manifest.Artifact("base.apk"),
                Manifest.Artifact("base-x86_64.apk")
            )
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "15")
                .setBody("base-x86_64.apk")
        )
        selfUpdate.download(release)
        webServer.takeRequest()
        webServer.takeRequest()

        val newRelease = SelfUpdate.Release(
            versionName = "1.0",
            versionCode = 1L,
            notes = null,
            tags = emptySet(),
            manifestUrl = webServer.url("/"),
            artifacts = listOf(Manifest.Artifact("base.apk"))
        )
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "8")
                .setBody("base.apk")
        )

        selfUpdate.download(newRelease)

        assertThat(webServer.takeRequest()).prop(RecordedRequest::path)
            .isEqualTo("/base.apk")

        // has downloaded artifacts fully
        val sessionInfo = installer.mySessions.first()
        installer.openSession(sessionInfo.sessionId).use { session ->
            assertThat(session).hasArtifactContents("base.apk")
        }
    }
}

private fun Assert<PackageInstallerCompat.Session>.hasArtifactContents(vararg contents: String) =
    given { session ->
        val names = session.names
        assertThat(names).hasSize(contents.size)
        contents.forEachIndexed { index, expectedContents ->
            val actualContents = String(session.openRead(names[index]).use { it.readAllBytes() })
            assertThat(names, name = "names").index(index)
                .assertThat(actualContents).isEqualTo(expectedContents)
        }
    }