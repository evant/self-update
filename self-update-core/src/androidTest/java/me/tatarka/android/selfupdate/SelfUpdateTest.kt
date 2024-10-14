package me.tatarka.android.selfupdate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import me.tatarka.android.selfupdate.manifest.Manifest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.internal.duplex.MockDuplexResponseBody
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Duration
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class SelfUpdateTest {
    @get:Rule
    val webServer = MockWebServer()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val installer = context.packageManager.packageInstaller
    private val selfUpdate = SelfUpdate(
        context = context, client = OkHttpClient.Builder()
            .readTimeout(Duration.ofMillis(100))
            .build()
    )

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

        val session = installer.mySessions.first()
        // size is set to Content-Length
        assertThat(session.size).isEqualTo(8)
        // has progress
        assertThat(session.progress).isBetween(0f, 1f)
        // has downloaded artifact fully
        installer.openSession(session.sessionId).use {
            val name = it.names.first()
            assertThat(it.openRead(name).readAllBytes()).isEqualTo("base.apk".toByteArray())
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
    fun resumes_partial_download() = runTest {
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
        // hang the second response body to ensure it fails to download
        val body = MockDuplexResponseBody()
        webServer.enqueue(
            MockResponse()
                .addHeader("Content-Length", "15")
                .setBody(body)
        )

        try {
            selfUpdate.download(release)
        } catch (e: IOException) {
            // expected timeout
        }
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
        val session = installer.mySessions.first()
        installer.openSession(session.sessionId).use {
            assertThat(it.openRead(it.names[0]).readAllBytes()).isEqualTo("base.apk".toByteArray())
            assertThat(
                it.openRead(it.names[1]).readAllBytes()
            ).isEqualTo("base-x86_64.apk".toByteArray())
        }
    }
}