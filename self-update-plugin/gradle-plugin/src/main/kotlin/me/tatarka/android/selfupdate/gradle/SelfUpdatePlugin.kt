package me.tatarka.android.selfupdate.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.component.analytics.AnalyticsEnabledSigningConfig
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.SigningConfig
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Sync
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class SelfUpdatePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withId("com.android.application") {
            val android = project.extensions.getByType(AppExtension::class.java)
            val androidComponents =
                project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            val aapt2Path = androidComponents.sdkComponents.sdkDirectory.map {
                it.file("build-tools/${android.buildToolsVersion}/aapt2")
            }

            androidComponents.registerExtension(
                DslExtension.Builder("selfUpdate")
                    .extendProjectWith(ProjectSelfUpdateExtension::class.java)
                    .extendBuildTypeWith(SelfUpdateExtension::class.java)
                    .extendProductFlavorWith(SelfUpdateExtension::class.java)
                    .build()
            ) { config ->
                val projectConfig =
                    (android as ExtensionAware).extensions.getByType<ProjectSelfUpdateExtension>()
                MergedSelfUpdateConfiguration(
                    config = config,
                    projectConfig = projectConfig,
                )
            }

            val defaultOutputDir = project.layout.buildDirectory.dir("outputs/selfupdate")

            androidComponents.finalizeDsl { a ->
                val projectConfig =
                    (android as ExtensionAware).extensions.getByType<ProjectSelfUpdateExtension>()

                projectConfig.mergeVariants.finalizeValue()
                projectConfig.base.update.finalizeValue()


                if (projectConfig.mergeVariants.getOrElse(false) == true) {
                    val packageSelfUpdate = project.tasks.register<PackageSelfUpdate>(
                        "packageSelfUpdate",
                        ""
                    )
                    packageSelfUpdate.configure {
                        output.convention(defaultOutputDir)
                    }

                    val copyArtifacts = project.tasks.register<Sync>("copySelfUpdateArtifacts") {
                        duplicatesStrategy = DuplicatesStrategy.FAIL
                        into(packageSelfUpdate.flatMap { it.output })
                    }
                    val mergeManifest =
                        project.tasks.register<MergeSelfUpdateManifests>("mergeSelfUpdateManifests") {
                            dependsOn(copyArtifacts)
                            output.set(project.layout.buildDirectory.file("intermediates/selfupdate/manifest.json"))
                            projectConfig.base.manifest.finalizeValue()
                            if (projectConfig.base.manifest.isPresent) {
                                manifests.from(projectConfig.base.manifest)
                            }
                        }
                    if (projectConfig.base.update.getOrElse(false) == true) {
                        val updateBaseManifest =
                            project.tasks.register("updateBaseManifest") {
                                inputs.file(packageSelfUpdate.flatMap { it.manifest })
                                doFirst {
                                    project.copy {
                                        from(packageSelfUpdate.get().manifest.get().asFile)
                                        val manifestFile = projectConfig.base.manifest.get().asFile
                                        into(manifestFile.parentFile)
                                        rename { manifestFile.name }
                                    }
                                }
                            }
                        packageSelfUpdate.dependsOn(mergeManifest)
                        packageSelfUpdate.configure {
                            finalizedBy(updateBaseManifest)
                        }
                    }
                } else {
                    project.tasks.register("packageSelfUpdate")
                }
            }

            androidComponents.onVariants { variant ->
                val projectConfig =
                    (android as ExtensionAware).extensions.getByType<ProjectSelfUpdateExtension>()
                val variantConfig = variant.getExtension(MergedSelfUpdateConfiguration::class.java)

                if (variantConfig == null || variantConfig.enabled.getOrElse(true)) {
                    val createArtifacts = project.tasks.register<ExtractApkSplits>(
                        "extractApkSplits${variant.name.capitalized()}"
                    ) {
                        aapt2.set(aapt2Path)
                        val outputPath =
                            project.layout.buildDirectory.dir("intermediates/bundle_apk_splits/${variant.name}")
                        output.set(outputPath)
                        version.set(outputPath.map { it.file("version") })
                        if (variantConfig != null) {
                            includeUniversal.set(variantConfig.includeUniversal)
                        }

                        val signingConfigName = variant.signingConfig.name
                        if (signingConfigName != null) {
                            val signingConfig = android.signingConfigs.getByName(signingConfigName)
                            if (signingConfig.storeFile != null) {
                                keystore.set(signingConfig.storeFile)
                                keystoreAlias.set(signingConfig.keyAlias)
                                keystorePassword.set(signingConfig.storePassword)
                                keyPassword.set(signingConfig.keyPassword)
                            }
                        }
                    }
                    variant.artifacts.use(createArtifacts)
                        .wiredWith(ExtractApkSplits::appBundle)
                        .toListenTo(SingleArtifact.BUNDLE)

                    val generateManifest = project.tasks.register<GenerateSelfUpdateManifest>(
                        "generate${variant.name.capitalized()}SelfUpdateManifest"
                    ) {
                        artifacts.set(createArtifacts.flatMap { it.output })
                        version.convention(createArtifacts.flatMap { task ->
                            task.version.asFile
                                .filter { it.exists() }
                                .map { file ->
                                    file.bufferedReader().use {
                                        ManifestVersion.parse(it)
                                    }
                                }
                        })
                        if (variantConfig != null) {
                            tags.convention(variantConfig.tags)
                            notes.convention(variantConfig.notes)
                            universalArtifact.convention(
                                variantConfig.includeUniversal.flatMap {
                                    if (it) {
                                        createArtifacts.flatMap { it.output.file("universal.apk") }
                                    } else {
                                        project.provider { null }
                                    }
                                }
                            )
                        }
                    }

                    if (projectConfig.mergeVariants.getOrElse(false) == true) {
                        val packageSelfUpdate =
                            project.tasks.named<PackageSelfUpdate>("packageSelfUpdate")

                        val suffix = (variantConfig?.artifactSuffix ?: projectConfig.artifactSuffix)
                            .orElse(generateManifest.flatMap { it.version.map { version -> "${variant.name}-${version.code}" } })

                        generateManifest.configure {
                            output.set(project.layout.buildDirectory.file("intermediates/selfupdate/${variant.name}/manifest.json"))
                            artifactSuffix.set(suffix)
                        }

                        val mergeManifests =
                            project.tasks.named<MergeSelfUpdateManifests>("mergeSelfUpdateManifests") {
                                manifests.from(generateManifest.map { it.output })
                                variants.add(variant.name)
                            }

                        val copyArtifacts = project.tasks.named<Sync>("copySelfUpdateArtifacts") {
                            from(mergeManifests.flatMap { it.output })
                            if (variantConfig != null) {
                                if (variantConfig.includeUniversal.getOrElse(false)) {
                                    from(createArtifacts.flatMap { it.output.file("universal.apk") })
                                }
                            }
                            from(createArtifacts.map { it.output.dir("splits") })
                            rename { name ->
                                renameApk(name, suffix.getOrElse(""))
                            }
                        }

                        packageSelfUpdate.dependsOn(copyArtifacts, mergeManifests)
                    } else {
                        val packageSelfUpdate = project.tasks.named("packageSelfUpdate")

                        val packageVariantSelfUpdate = project.tasks.register<PackageSelfUpdate>(
                            "package${variant.name.capitalized()}SelfUpdate",
                            variant.name
                        )
                        packageVariantSelfUpdate.configure {
                            output.set(defaultOutputDir.map { it.dir(variant.name) })
                        }

                        val suffix =
                            (variantConfig?.artifactSuffix ?: projectConfig.artifactSuffix).orElse(
                                generateManifest.flatMap { it.version.map { version -> "-${version.code}" } })

                        generateManifest.configure {
                            output.set(project.layout.buildDirectory.file("intermediates/selfupdate/manifest.json"))
                            artifactSuffix.set(suffix)
                        }

                        val copyArtifacts = project.tasks.register<Sync>(
                            "copy${variant.name.capitalized()}SelfUpdateArtifacts"
                        ) {
                            duplicatesStrategy = DuplicatesStrategy.FAIL
                            from(generateManifest.flatMap { it.output })
                            if (variantConfig != null) {
                                if (variantConfig.includeUniversal.getOrElse(false)) {
                                    from(createArtifacts.flatMap { it.output.file("universal.apk") })
                                }
                            }
                            from(createArtifacts.flatMap { it.output.file("splits") })
                            rename { name ->
                                renameApk(name, suffix.getOrElse(""))
                            }
                            into(packageVariantSelfUpdate.flatMap { it.output })
                        }

                        packageVariantSelfUpdate.dependsOn(copyArtifacts, generateManifest)
                        packageSelfUpdate.dependsOn(packageVariantSelfUpdate)
                    }
                }
            }
        }
    }
}

private fun renameApk(name: String, suffix: String): String {
    return if (name.endsWith(".apk")) {
        name.removeSuffix(".apk") + suffix + ".apk"
    } else {
        name
    }
}

private val SigningConfig.name: String?
    get() = when (this) {
        is SigningConfigImpl -> name
        is AnalyticsEnabledSigningConfig -> delegate.name
        else -> null
    }