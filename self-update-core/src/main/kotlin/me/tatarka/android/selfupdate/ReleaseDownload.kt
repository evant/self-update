package me.tatarka.android.selfupdate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import me.tatarka.android.selfupdate.SelfUpdate.Release
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.use
import ru.gildor.coroutines.okhttp.await
import java.io.IOException
import java.io.OutputStream

internal suspend fun download(
    release: Release,
    metadata: ReleaseMetadata,
    client: OkHttpClient = OkHttpClient()
): DownloadResponse {
    val artifactResponses = mutableListOf<ArtifactResponse>()
    val artifactNamer = ArtifactNamer()
    val sameVersion = release.versionCode == metadata.versionCode
    for (artifact in release.artifacts) {
        val path = artifact.path
        val url = release.manifestUrl.relativePath(path)
        val name = artifactNamer.name(url)
        val artifactMetadata = metadata.artifacts[name] ?: ReleaseMetadata.ArtifactMetadata().also {
            it.checksum = artifact.checksums?.firstOrNull()
            metadata.artifacts[name] = it
        }
        val shouldDownload = if (sameVersion) {
            if (artifactMetadata.checksum != artifact.checksums?.firstOrNull()) {
                // checksums don't match, mark for re-download
                artifactMetadata.bytesWritten = 0
            }
            !artifactMetadata.isDownloadComplete()
        } else {
            true
        }
        if (shouldDownload) {
            val response = requestArtifact(
                name = name,
                url = url,
                bytesWritten = artifactMetadata.bytesWritten,
                client = client,
            )
            artifactResponses.add(response)
        }
    }
    return DownloadResponse(artifactResponses = artifactResponses, client = client)
}

private suspend fun requestArtifact(
    name: String,
    url: HttpUrl,
    bytesWritten: Long,
    client: OkHttpClient
): ArtifactResponse {
    val response = client.newCall(
        Request.Builder()
            .url(url)
            .addHeader(
                "Range",
                "bytes=${bytesWritten}-"
            )
            .build()
    ).await()
    val body = response.ensureBody()
    val contentSize = response.header("Content-Length")?.toLong() ?: -1
    val rangeHeader = response.header("Content-Range")
    val bytesWritten: Long
    val size: Long
    if (response.code == 206 && rangeHeader != null) {
        val range = parseRangeHeader(rangeHeader)
        bytesWritten = range.start
        size = range.size
    } else {
        // Range request not supported, expect full response
        bytesWritten = 0L
        size = contentSize
    }
    return ArtifactResponse(
        name = name,
        url = url,
        size = size,
        bytesWritten = bytesWritten,
        body = body,
    )
}

private fun parseRangeHeader(header: String): Range {
    // only support bytes
    if (!header.startsWith("bytes")) {
        throw IOException("failed to parse Range header: $header")
    }
    val start = header.substringAfter("bytes ", "")
        .substringBefore("-", "")
        .toLongOrNull()
    val end = header.substringAfter("-", "")
        .substringBefore("/", "")
        .toLongOrNull()
    val size = header.substringAfter("/", "")
        .toLongOrNull()

    if (start == null || end == null || size == null) {
        // failed to parse header
        throw IOException("failed to parse Range header: $header")
    }
    return Range(start, end, size)
}

private class Range(
    val start: Long,
    val end: Long,
    val size: Long
)

internal class DownloadResponse internal constructor(
    private val client: OkHttpClient,
    private val artifactResponses: List<ArtifactResponse>
) {
    val estimatedSize: Long
        get() = artifactResponses.sumOf { it.size }

    suspend fun write(
        onProgress: (Float) -> Unit,
        output: (name: String, offset: Long, size: Long) -> OutputStream,
        complete: (name: String, size: Long) -> Unit = { _, _ -> },
    ) {
        val artifactCount = artifactResponses.size
        if (artifactCount == 0) return
        withContext(Dispatchers.IO) {
            artifactResponses.forEachIndexed { index, response ->
                var currentResponse = response
                while (true) {
                    yield()
                    val actualContentSize = output(
                        currentResponse.name,
                        currentResponse.bytesWritten,
                        currentResponse.size,
                    ).use { dest ->
                        currentResponse.body.byteStream().buffered().use { src ->
                            if (currentResponse.size > 0) {
                                src.copyTo(
                                    ProgressOutputStream(
                                        dest,
                                        currentResponse.bytesWritten,
                                        currentResponse.size
                                    ) {
                                        onProgress(it * (index + 1) / artifactCount)
                                    }
                                )
                            } else {
                                src.copyTo(dest)
                            }
                        }
                    }
                    val newBytesWritten = currentResponse.bytesWritten + actualContentSize
                    if (newBytesWritten < currentResponse.size) {
                        // only had a partial response, make another range request
                        currentResponse = requestArtifact(
                            name = currentResponse.name,
                            url = currentResponse.url,
                            bytesWritten = newBytesWritten,
                            client = client,
                        )
                    } else {
                        break
                    }
                }
                complete(currentResponse.name, currentResponse.size)
            }
        }
    }
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


private class ArtifactNamer {
    private var index = 0

    fun name(url: HttpUrl): String {
        val simpleName = url.pathSegments.last().removeSuffix(".apk")
        val uniqueName = "${simpleName}_${index}.apk"
        index += 1
        return uniqueName
    }
}

internal class ArtifactResponse(
    val name: String,
    val url: HttpUrl,
    val size: Long,
    val bytesWritten: Long,
    val body: ResponseBody,
)

private class ProgressOutputStream(
    private val output: OutputStream,
    offset: Long,
    private val size: Long,
    private val onProgress: (Float) -> Unit
) : OutputStream() {
    private var bytesWritten = offset

    override fun write(b: ByteArray, off: Int, len: Int) {
        bytesWritten += len
        output.write(b, off, len)
        onProgress(progress())
    }

    override fun write(b: Int) {
        bytesWritten += 1
        output.write(b)
        onProgress(progress())
    }

    override fun close() {
        output.close()
    }

    private fun progress(): Float = (bytesWritten.toDouble() / size).toFloat()
}
