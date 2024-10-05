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
import org.gradle.api.tasks.Copy
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

            androidComponents.finalizeDsl {
                val projectConfig =
                    (android as ExtensionAware).extensions.getByType<ProjectSelfUpdateExtension>()

                projectConfig.mergeVariants.finalizeValue()
                projectConfig.base.update.finalizeValue()

                if (projectConfig.mergeVariants.getOrElse(false) == true) {
                    val packageSelfUpdate =
                        project.tasks.register<PackageSelfUpdate>("packageSelfUpdate") {
                            output.convention(defaultOutputDir)
                        }

                    val copyArtifacts = project.tasks.register<Copy>("copySelfUpdateArtifacts") {
                        doFirst {
                            project.delete(packageSelfUpdate.get().output.get())
                        }
                        duplicatesStrategy = DuplicatesStrategy.FAIL
                        into(packageSelfUpdate.flatMap { it.output })
                    }
                    val mergeManifest =
                        project.tasks.register<MergeSelfUpdateManifests>("mergeSelfUpdateManifests") {
                            dependsOn(copyArtifacts)
                            output.set(packageSelfUpdate.flatMap { it.output.file("manifest.json") })
                            projectConfig.base.manifest.finalizeValue()
                            if (projectConfig.base.manifest.isPresent) {
                                manifests.from(projectConfig.base.manifest)
                            }
                        }
                    if (projectConfig.base.update.getOrElse(false) == true) {
                        val updateBaseManifest =
                            project.tasks.register("updateBaseManifest") {
                                inputs.file(mergeManifest.map { it.output })
                                doFirst {
                                    project.copy {
                                        from(mergeManifest.get().output.get().asFile)
                                        val manifestFile = projectConfig.base.manifest.get().asFile
                                        into(manifestFile.parentFile)
                                        rename { manifestFile.name }
                                    }
                                }
                            }
                        packageSelfUpdate.dependsOn(updateBaseManifest)
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
                        version.convention(createArtifacts.flatMap {
                            it.version.asFile.map { file ->
                                val (versionName, versionCode) = file.readText().lines()
                                Version(versionName, versionCode.toLong())
                            }
                        })
                        if (variantConfig != null) {
                            tags.convention(variantConfig.tags)
                            notes.convention(variantConfig.notes)
                        }
                    }

                    if (projectConfig.mergeVariants.getOrElse(false) == true) {
                        val packageSelfUpdate = project.tasks.named<PackageSelfUpdate>("packageSelfUpdate")
                        
                        val suffix = (variantConfig?.artifactSuffix ?: projectConfig.artifactSuffix)
                            .orElse(generateManifest.flatMap { it.version.map { version -> "${variant.name}-${version.code}" } })

                        generateManifest.configure {
                            output.set(project.layout.buildDirectory.file("intermediates/selfupdate/${variant.name}/manifest.json"))
                            artifactSuffix.set(suffix)
                        }

                        val copyArtifacts = project.tasks.named<Copy>("copySelfUpdateArtifacts") {
                            from(createArtifacts.map { it.output.dir("splits") }) {
                                rename { name ->
                                    name.removeSuffix(".apk") + suffix.getOrElse("") + ".apk"
                                }
                            }
                        }

                        val mergeManifests =
                            project.tasks.named<MergeSelfUpdateManifests>("mergeSelfUpdateManifests") {
                                dependsOn(copyArtifacts)
                                manifests.from(generateManifest.map { it.output })
                                variants.add(variant.name)
                            }

                        packageSelfUpdate.dependsOn(copyArtifacts, mergeManifests)
                    } else {
                        val packageSelfUpdate = project.tasks.named("packageSelfUpdate")
                        
                        val packageVariantSelfUpdate = project.tasks.register<PackageSelfUpdate>(
                            "package${variant.name.capitalized()}SelfUpdate"
                        ) {
                            variantName.set(variant.name)
                            output.set(defaultOutputDir.map { it.dir(variant.name) })
                        }

                        val suffix =
                            (variantConfig?.artifactSuffix ?: projectConfig.artifactSuffix).orElse(
                                generateManifest.flatMap { it.version.map { version -> "-${version.code}" } })

                        generateManifest.configure {
                            output.set(packageVariantSelfUpdate.flatMap { it.output.file("manifest.json") })
                            artifactSuffix.set(suffix)
                        }

                        val copyArtifacts = project.tasks.register<Copy>(
                            "copy${variant.name.capitalized()}SelfUpdateArtifacts"
                        ) {
                            doFirst {
                                project.delete(packageVariantSelfUpdate.get().output.get())
                            }
                            duplicatesStrategy = DuplicatesStrategy.FAIL
                            from(createArtifacts.flatMap { it.output.file("splits") })
                            rename { name ->
                                name.removeSuffix(".apk") + suffix.getOrElse("") + ".apk"
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

private val SigningConfig.name: String?
    get() = when (this) {
        is SigningConfigImpl -> name
        is AnalyticsEnabledSigningConfig -> delegate.name
        else -> null
    }