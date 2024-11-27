package me.tatarka.android.selfupdate.gradle

import me.tatarka.android.selfupdate.manifest.Manifest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class PublishGithubRelease : DefaultTask() {
    @get:InputDirectory
    abstract val release: DirectoryProperty

    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val token: Property<String>

    @get:Input
    abstract val owner: Property<String>

    @get:Input
    abstract val repo: Property<String>

    @get:Input
    abstract val tagName: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseName: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseBody: Property<String>

    @get:Input
    @get:Optional
    abstract val prerelease: Property<Boolean>

    @get:OutputFile
    abstract val finalManifest: RegularFileProperty

    init {
        group = PublishingPlugin.PUBLISH_TASK_GROUP
        description = "Publishes self-update releases as github releases"
    }

    @TaskAction
    fun run() {
        val api = GithutReleaseApi()

        val manifestFile = manifest.get().asFile
        val finalManifestFile = finalManifest.get().asFile
        val artifactDir = release.get().asFile
        val artifactFiles = release.get().asFileTree.matching { include("*.apk") }.files
        val versionName = tagName.get()
        val manifest = manifestFile.inputStream().buffered().use { Manifest.parse(it) }

        val token = token.get()
        val owner = owner.get()
        val repo = repo.get()
        val githubRelease = api.createRelease(
            token = token,
            owner = owner,
            repo = repo,
            tagName = versionName,
            name = releaseName.orNull,
            body = releaseBody.orNull,
            prerelease = prerelease.orNull,
        )

        val uploadUrl = githubRelease.upload_url.replace(Regex("""\{.*\}"""), "")

        val artifactUrls = mutableMapOf<String, String>()

        for (artifactFile in artifactFiles) {
            val response = api.uploadAsset(
                token = token,
                url = uploadUrl,
                file = artifactFile.toPath(),
                contentType = "application/vnd.android.package-archive"
            )
            artifactUrls[artifactFile.relativeTo(artifactDir).path] = response.browser_download_url
        }

        val updatedManifest = manifest.copy(
            releases = manifest.releases.map { release ->
                release.copy(
                    artifacts = release.artifacts.map {
                        val url = artifactUrls[it.path]
                        if (url != null) {
                            it.copy(path = url)
                        } else {
                            it
                        }
                    }
                )
            }
        )

        finalManifestFile.outputStream().buffered().use {
            updatedManifest.write(it)
        }

        api.uploadAsset(
            token = token,
            url = uploadUrl,
            file = finalManifestFile.toPath(),
            contentType = "application/json"
        )

        logger.warn(
            """created release: ${githubRelease.html_url}
            |manifest url: https://github.com/${owner}/${repo}/releases/latest/download/${manifestFile.name}
        """.trimMargin()
        )
    }
}