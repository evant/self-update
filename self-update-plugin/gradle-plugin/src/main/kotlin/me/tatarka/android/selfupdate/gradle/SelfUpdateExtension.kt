package me.tatarka.android.selfupdate.gradle

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import org.gradle.api.Action
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import org.gradle.kotlin.dsl.configure
import java.io.Serializable

interface SelfUpdateExtension : SelfUpdateConfiguration {
    override val enabled: Property<Boolean>
    override val includeUniversal: Property<Boolean>
    override val tags: SetProperty<String>
    override val notes: Property<String>
    override val artifactSuffix: Property<String>

    @get:Nested
    override val base: Base

    fun base(action: Action<Base>) {
        action.execute(base)
    }

    interface Base : SelfUpdateConfiguration.Base {
        override val manifest: RegularFileProperty
        override val update: Property<Boolean>
    }
}

interface ProjectSelfUpdateExtension : SelfUpdateExtension, ProjectSelfUpdateConfiguration {
    override val mergeVariants: Property<Boolean>
}

interface ProjectSelfUpdateConfiguration : SelfUpdateConfiguration {
    val mergeVariants: Provider<Boolean>
}

interface SelfUpdateConfiguration {
    val enabled: Provider<Boolean>
    val includeUniversal: Provider<Boolean>
    val tags: Provider<Set<String>>
    val notes: Provider<String>
    val artifactSuffix: Provider<String>
    val base: Base

    interface Base {
        val manifest: Provider<RegularFile>
        val update: Provider<Boolean>
    }
}

class MergedSelfUpdateConfiguration(
    val enabled: Provider<Boolean>,
    val includeUniversal: Provider<Boolean>,
    val tags: Provider<Set<String>>,
    val notes: Provider<String>,
    val artifactSuffix: Provider<String>,
    val base: MergedBase,
    val mergeVariants: Provider<Boolean>,
) : VariantExtension, Serializable {

    class MergedBase(
        val manifest: Provider<RegularFile>,
        val update: Provider<Boolean>
    )
}

internal fun MergedSelfUpdateConfiguration(
    config: VariantExtensionConfig<ApplicationVariant>,
    projectConfig: ProjectSelfUpdateExtension,
): MergedSelfUpdateConfiguration {
    val configs = sequenceOf(
        projectConfig,
        config.buildTypeExtension(SelfUpdateExtension::class.java),
        *config.productFlavorsExtensions(SelfUpdateExtension::class.java).toTypedArray(),
    )
    return MergedSelfUpdateConfiguration(
        enabled = configs.map { it.enabled }.merge(),
        includeUniversal = configs.map { it.includeUniversal }.merge(),
        tags = configs.map { it.tags }.merge(),
        notes = configs.map { it.notes }.merge(),
        artifactSuffix = configs.map { it.artifactSuffix }.merge(),
        base = MergedSelfUpdateConfiguration.MergedBase(
            manifest = configs.map { it.base.manifest }.merge(),
            update = configs.map { it.base.update }.merge()
        ),
        mergeVariants = projectConfig.mergeVariants,
    )
}

private fun <T> Sequence<Provider<T>>.merge(): Provider<T> = reduce { a, b -> b.orElse(a) }

fun BuildType.selfUpdate(action: Action<SelfUpdateExtension>) {
    extensions.configure(SelfUpdateExtension::class, action)
}

fun ProductFlavor.selfUpdate(action: Action<SelfUpdateExtension>) {
    extensions.configure(SelfUpdateExtension::class, action)
}