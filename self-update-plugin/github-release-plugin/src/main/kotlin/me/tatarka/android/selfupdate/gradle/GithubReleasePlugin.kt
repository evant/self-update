package me.tatarka.android.selfupdate.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

class GithubReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val config = project.extensions.create<GithubReleaseExtension>("githubRelease")
        project.tasks.withType<PackageSelfUpdate>().whenTaskAdded {
            registerTask(project, this, config)
        }
    }

    private fun registerTask(
        project: Project,
        packageTask: PackageSelfUpdate,
        config: GithubReleaseExtension
    ) {
        project.tasks.register<PublishGithubRelease>(
            "publish${packageTask.variantName.capitalized()}ToGithubRelease"
        ) {
            val finalManifestPath = buildString { 
                append("selfupdate-githubrelease")
                if (packageTask.variantName.isNotEmpty()) {
                    append("/${packageTask.variantName}")
                }
                append("/${packageTask.manifestName.get()}")
            }
            release.set(packageTask.output)
            manifest.set(packageTask.manifest)
            finalManifest.set(project.layout.buildDirectory.file(finalManifestPath))
            token.convention(
                config.token.orElse(
                    project.providers.gradleProperty("selfupdate.githubReleaseToken")
                )
            )
            owner.convention(config.owner)
            repo.convention(config.repo)
            tagName.convention(
                config.tagName.orElse(project.provider { project.version.toString() })
            )
            releaseName.convention(config.name)
            releaseBody.convention(config.body)
            prerelease.convention(config.prerelease)
        }
    }
}