package me.tatarka.android.selfupdate

import assertk.assertThat
import assertk.assertions.hasText
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test

class DownloadTest {
    @get:Rule
    val webServer = MockWebServer()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun downloads_successfully() = runTest {
        webServer.enqueue(MockResponse().setBody("base.apk"))

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                manifestUrl = webServer.url("/"),
                artifacts = listOf(me.tatarka.android.selfupdate.manifest.Manifest.Artifact("base.apk"))
            )
        )
        var apkName = ""
        response.write(onProgress = {}) { name, _ ->
            apkName = name
            tempDir.resolve(name).outputStream().buffered()
        }

        assertThat(tempDir.resolve(apkName))
            .hasText("base.apk")
    }

    @Test
    fun downloads_all_artifacts() = runTest {
        webServer.enqueue(MockResponse().setBody("base.apk"))
        webServer.enqueue(MockResponse().setBody("split.apk"))

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                manifestUrl = webServer.url("/"),
                artifacts = listOf(
                    me.tatarka.android.selfupdate.manifest.Manifest.Artifact("base.apk"),
                    me.tatarka.android.selfupdate.manifest.Manifest.Artifact("split.apk")
                )
            )
        )
        val apkNames = mutableListOf<String>()
        response.write(onProgress = {}) { name, _ ->
            apkNames.add(name)
            tempDir.resolve(name).outputStream().buffered()
        }

        assertThat(tempDir.resolve(apkNames[0]))
            .hasText("base.apk")
        assertThat(tempDir.resolve(apkNames[1]))
            .hasText("split.apk")
    }

    @Test
    fun reports_progress_when_content_length_set() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "8")
                .setBody("base.apk")
        )

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                manifestUrl = webServer.url("/"),
                artifacts = listOf(me.tatarka.android.selfupdate.manifest.Manifest.Artifact("base.apk"))
            )
        )
        var progress = 0f
        response.write(onProgress = { progress = it }) { _, _ ->
            tempDir.resolve("base.apk").outputStream().buffered()
        }

        assertThat(progress).isEqualTo(1f)
    }

    @Test
    fun reports_progress_on_multiple_artifacts() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "8")
                .setBody("base.apk")
        )
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "8")
                .setBody("split.apk")
        )

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                manifestUrl = webServer.url("/"),
                artifacts = listOf(
                    me.tatarka.android.selfupdate.manifest.Manifest.Artifact("base.apk"),
                    me.tatarka.android.selfupdate.manifest.Manifest.Artifact("split.apk")
                )
            )
        )
        val progresses = mutableListOf<Float>()
        response.write(onProgress = { progresses.add(it) }) { _, _ ->
            tempDir.resolve("base.apk").outputStream().buffered()
        }

        assertThat(progresses).isEqualTo(listOf(0.5f, 1f))
    }
}