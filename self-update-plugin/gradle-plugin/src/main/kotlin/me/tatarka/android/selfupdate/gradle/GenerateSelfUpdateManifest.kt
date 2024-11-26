package me.tatarka.android.selfupdate.gradle

import com.android.build.gradle.internal.tasks.BaseTask
import kotlinx.serialization.ExperimentalSerializationApi
import me.tatarka.android.selfupdate.manifest.Manifest
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
@OptIn(ExperimentalSerializationApi::class)
abstract class GenerateSelfUpdateManifest : BaseTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifacts: DirectoryProperty

    @get:Input
    abstract val version: Property<ManifestVersion>

    @get:Input
    @get:Optional
    abstract val tags: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val notes: Property<String>

    @get:Input
    @get:Optional
    abstract val artifactSuffix: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        val version = version.get()
        val apkSplitsPath = artifacts.get().asFile
        val artifacts = parseArtifactMetadata(apkSplitsPath.resolve("toc.pb")) { path ->
            path.removeSurrounding(prefix = "splits/", suffix = ".apk") +
                    artifactSuffix.getOrElse("") + ".apk"
        }
        val manifestPath = output.get().asFile
        manifestPath.outputStream().buffered().use {
            Manifest(
                releases = listOf(
                    Manifest.Release(
                        version_name = version.name,
                        version_code = version.code,
                        tags = tags.getOrElse(emptySet()),
                        notes = notes.orNull,
                        minSdk = version.minSdk,
                        maxSdk = version.maxSdk,
                        artifacts = artifacts,
                        meta = Manifest.Meta(
                            version = 1,
                            feature_version = 1,
                        )
                    )
                )
            ).write(it)
        }
    }
}
