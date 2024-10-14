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
import java.io.OutputStream

internal suspend fun download(
    release: Release,
    skip: Set<String> = emptySet(),
    client: OkHttpClient = OkHttpClient()
): DownloadResponse {
    val artifactResponses = mutableListOf<ArtifactResponse>()
    val artifactNamer = ArtifactNamer(release.versionCode)
    for (artifact in release.artifacts) {
        val path = artifact.path
        val url = release.manifestUrl.relativePath(path)
        val name = artifactNamer.name(url)
        if (name !in skip) {
            val response = client.newCall(Request.Builder().url(url).build()).await()
            val body = response.ensureBody()
            val size = response.header("Content-Length")?.toLong() ?: 0
            artifactResponses.add(
                ArtifactResponse(
                    name = name,
                    size = size,
                    body = body,
                )
            )
        }
    }
    return DownloadResponse(artifactResponses = artifactResponses)
}

internal class DownloadResponse internal constructor(private val artifactResponses: List<ArtifactResponse>) {
    val estimatedSize: Long
        get() = artifactResponses.sumOf { it.size }

    suspend fun write(
        onProgress: (Float) -> Unit,
        output: (name: String, size: Long) -> OutputStream,
        complete: (name: String, size: Long) -> Unit = { _, _ -> },
    ) {
        val artifactCount = artifactResponses.size
        if (artifactCount == 0) return
        withContext(Dispatchers.IO) {
            artifactResponses.forEachIndexed { index, response ->
                yield()
                output(
                    response.name,
                    if (response.size > 0) response.size else -1
                ).use { dest ->
                    response.body.byteStream().buffered().use { src ->
                        if (response.size > 0) {
                            src.copyTo(ProgressOutputStream(dest, response.size) {
                                onProgress(it * (index + 1) / artifactCount)
                            })
                        } else {
                            src.copyTo(dest)
                        }
                    }
                }
                complete(response.name, response.size)
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

private class ArtifactNamer(private val versionCode: Long) {
    private var index = 0

    fun name(url: HttpUrl): String {
        val simpleName = url.pathSegments.last().removeSuffix(".apk")
        val uniqueName = "${simpleName}_${index}_${versionCode}.apk"
        index += 1
        return uniqueName
    }
}

internal class ArtifactResponse(
    val name: String,
    val size: Long,
    val body: ResponseBody,
)

private class ProgressOutputStream(
    private val output: OutputStream,
    private val size: Long,
    private val onProgress: (Float) -> Unit
) : OutputStream() {
    private var bytesWritten = 0L

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
