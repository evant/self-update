package me.tatarka.android.selfupdate.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.internal.cc.base.logger
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandler
import java.net.http.HttpResponse.BodySubscribers
import java.nio.file.Path
import kotlin.io.path.name

@Serializable
internal class CreateReleaseBody(
    val tag_name: String,
    val draft: Boolean? = null,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean? = null,
)

@Serializable
internal class UpdateReleaseBody(
    val tag_name: String? = null,
    val draft: Boolean? = null,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean? = null,
)

@Serializable
internal class CreateReleaseResponse(
    val id: Long,
    val url: String,
    val upload_url: String,
    val html_url: String,
)

@Serializable
internal class UploadAssetResponse(
    val url: String,
    val browser_download_url: String,
)

@Serializable
internal class ErrorResponse(val message: String)

internal class GithutReleaseApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val client = HttpClient.newHttpClient()

    fun createRelease(
        token: String,
        owner: String,
        repo: String,
        tagName: String,
        draft: Boolean? = null,
        name: String? = null,
        body: String? = null,
        prerelease: Boolean? = null,
    ): CreateReleaseResponse {
        val url = "https://api.github.com/repos/${owner}/${repo}/releases"
        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .POST(
                json.toBodyPublisher(
                    CreateReleaseBody(
                        tag_name = tagName,
                        draft = draft,
                        name = name,
                        body = body,
                        prerelease = prerelease,
                    )
                )
            )
            .build()

        try {
            val response = client.send(request, json.toBodyHandler<CreateReleaseResponse>())
            return response.body().getOrThrow()
        } catch (e: IOException) {
            throw IOException("${e.message} POST $url", e)
        }
    }
    
    fun uploadAsset(
        url: String,
        token: String,
        file: Path,
        contentType: String,
    ): UploadAssetResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI("${url}?name=${file.name}"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", contentType)
            .POST(BodyPublishers.ofFile(file))
            .build()

        try {
            logger.info("uploading: $file")
            val response = client.send(request, json.toBodyHandler<UploadAssetResponse>())
            return response.body().getOrThrow()
        } catch (e: IOException) {
            throw IOException("${e.message} POST $url", e)
        }
    }
}

private inline fun <reified T> Json.toBodyPublisher(body: T): BodyPublisher {
    return BodyPublishers.ofString(encodeToString(body))
}

private inline fun <reified T> Json.toBodyHandler(): BodyHandler<Result<T>> {
    return BodyHandler { info ->
        BodySubscribers.mapping(
            BodySubscribers.ofString(Charsets.UTF_8)
        ) { body ->
            if (info.statusCode() in 200..299) {
                Result.success(decodeFromString<T>(body))
            } else {
                Result.failure(
                    IOException(
                        "request failed: ${info.statusCode()} ${
                            decodeFromString<ErrorResponse>(body).message
                        }"
                    )
                )
            }
        }
    }
}