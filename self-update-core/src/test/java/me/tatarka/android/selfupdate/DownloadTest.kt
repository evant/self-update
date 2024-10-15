package me.tatarka.android.selfupdate

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.hasText
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import me.tatarka.android.selfupdate.manifest.Manifest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            ),
            metadata = ReleaseMetadata(versionCode = 1)
        )
        var apkName = ""
        response.write(
            onProgress = {},
            output = { name, _, _ ->
                apkName = name
                tempDir.resolve(name).outputStream().buffered()
            }
        )

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
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(
                    Manifest.Artifact("base.apk"),
                    Manifest.Artifact("split.apk")
                ),
            ),
            metadata = ReleaseMetadata(versionCode = 1)
        )
        val apkNames = mutableListOf<String>()
        response.write(
            onProgress = {},
            output = { name, _, _ ->
                apkNames.add(name)
                tempDir.resolve(name).outputStream().buffered()
            }
        )

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
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            ),
            metadata = ReleaseMetadata(versionCode = 1)
        )
        var progress = 0f
        response.write(
            onProgress = { progress = it },
            output = { _, _, _ ->
                tempDir.resolve("base.apk").outputStream().buffered()
            }
        )

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
                .setHeader("Content-Length", "9")
                .setBody("split.apk")
        )

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(
                    Manifest.Artifact("base.apk"),
                    Manifest.Artifact("split.apk")
                )
            ),
            metadata = ReleaseMetadata(versionCode = 1)
        )
        val progresses = mutableListOf<Float>()
        response.write(
            onProgress = { progresses.add(it) },
            output = { _, _, _ ->
                tempDir.resolve("base.apk").outputStream().buffered()
            },
        )

        assertThat(progresses).isEqualTo(listOf(0.5f, 1f))
    }

    @Test
    fun skips_artifacts_with_completed_download() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "9")
                .setBody("split.apk")
        )

        val metadata = ReleaseMetadata(versionCode = 1).apply {
            artifacts["base_0.apk"] = ReleaseMetadata.ArtifactMetadata().apply {
                markDownloadComplete(8)
            }
        }

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(
                    Manifest.Artifact("base.apk"),
                    Manifest.Artifact("split.apk"),
                )
            ),
            metadata = metadata
        )

        val apkNames = mutableListOf<String>()
        response.write(
            onProgress = {},
            output = { name, _, _ ->
                apkNames.add(name)
                tempDir.resolve(name).outputStream().buffered()
            }
        )

        assertThat(apkNames).hasSize(1)
        assertThat(tempDir.resolve(apkNames[0]))
            .hasText("split.apk")
    }

    @Test
    fun redownloads_artifacts_if_version_code_changes() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "8")
                .setBody("base.apk")
        )

        val metadata = ReleaseMetadata(versionCode = 1).apply {
            artifacts["base_0.apk"] = ReleaseMetadata.ArtifactMetadata().apply {
                markDownloadComplete(8)
            }
        }

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 2L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            ),
            metadata = metadata
        )

        var apkName = ""
        response.write(
            onProgress = {},
            output = { name, _, _ ->
                apkName = name
                tempDir.resolve(name).outputStream().buffered()
            }
        )

        assertThat(tempDir.resolve(apkName))
            .hasText("base.apk")
    }

    @Test
    fun redownloads_if_checksums_dont_match() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "8")
                .setBody("base.apk")
        )

        val metadata = ReleaseMetadata(versionCode = 1).apply {
            artifacts["base_0.apk"] = ReleaseMetadata.ArtifactMetadata().apply {
                markDownloadComplete(8)
                checksum = "a"
            }
        }

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk", checksums = listOf("b")))
            ),
            metadata = metadata
        )

        var apkName = ""
        response.write(
            onProgress = {},
            output = { name, _, _ ->
                apkName = name
                tempDir.resolve(name).outputStream().buffered()
            }
        )

        assertThat(tempDir.resolve(apkName))
            .hasText("base.apk")
    }

    @Test
    fun resume_download_with_range_request_if_server_supports_it() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "8")
                .setHeader("Content-Range", "bytes 4-7/8")
                .setResponseCode(206)
                .setBody(".apk")
        )

        val metadata = ReleaseMetadata(versionCode = 1).apply {
            artifacts["base_0.apk"] = ReleaseMetadata.ArtifactMetadata().apply {
                bytesWritten = 4
            }
        }

        tempDir.resolve("base_0.apk").writeText("base")

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            ),
            metadata = metadata
        )

        var apkName = ""
        var apkOffset = -1L
        var apkSize = -1L
        response.write(
            onProgress = {},
            output = { name, offset, size ->
                apkName = name
                apkOffset = offset
                apkSize = size
                FileOutputStream(RandomAccessFile(tempDir.resolve(name), "rw").apply {
                    seek(offset)
                }.fd).buffered()
            }
        )

        assertThat(webServer.takeRequest().getHeader("Range"))
            .isEqualTo("bytes=4-")

        assertThat(apkOffset).isEqualTo(4)
        assertThat(apkSize).isEqualTo(8)

        assertThat(tempDir.resolve(apkName))
            .hasText("base.apk")
    }

    @Test
    fun resumes_download_with_range_request_if_content_length_header_is_missing() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Range", "bytes 4-7/8")
                .setResponseCode(206)
                .setBody(".apk")
        )

        val metadata = ReleaseMetadata(versionCode = 1).apply {
            artifacts["base_0.apk"] = ReleaseMetadata.ArtifactMetadata().apply {
                bytesWritten = 4
            }
        }

        tempDir.resolve("base_0.apk").writeText("base")

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            ),
            metadata = metadata
        )

        var apkName = ""
        response.write(
            onProgress = {},
            output = { name, offset, _ ->
                apkName = name
                FileOutputStream(RandomAccessFile(tempDir.resolve(name), "rw").apply {
                    seek(offset)
                }.fd).buffered()
            }
        )

        assertThat(webServer.takeRequest().getHeader("Range"))
            .isEqualTo("bytes=4-")

        assertThat(tempDir.resolve(apkName))
            .hasText("base.apk")
    }

    @Test
    fun makes_multiple_requests_if_server_decides_to_chunk_response() = runTest {
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "4")
                .setHeader("Content-Range", "bytes 0-3/8")
                .setResponseCode(206)
                .setBody("base")
        )
        webServer.enqueue(
            MockResponse()
                .setHeader("Content-Length", "4")
                .setHeader("Content-Range", "bytes 4-7/8")
                .setResponseCode(206)
                .setBody(".apk")
        )

        val metadata = ReleaseMetadata(versionCode = 1).apply {
            artifacts["base_0.apk"] = ReleaseMetadata.ArtifactMetadata().apply {
                bytesWritten = 4
            }
        }

        val response = download(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1L,
                notes = null,
                tags = emptySet(),
                manifestUrl = webServer.url("/"),
                artifacts = listOf(Manifest.Artifact("base.apk"))
            ),
            metadata = metadata
        )

        var apkName = ""
        response.write(
            onProgress = {},
            output = { name, offset, _ ->
                apkName = name
                FileOutputStream(RandomAccessFile(tempDir.resolve(name), "rw").apply {
                    seek(offset)
                }.fd).buffered()
            }
        )

        assertThat(tempDir.resolve(apkName))
            .hasText("base.apk")
    }
}