package me.tatarka.android.selfupdate.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

abstract class PackageSelfUpdate @Inject constructor(@get:Internal val variantName: String) :
    DefaultTask() {

    @get:Input
    abstract val manifestName: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Internal
    val manifest: Provider<RegularFile> get() = output.flatMap { it.file(manifestName) }

    init {
        group = "Build"
        description = "Generates a set of apks and manifest.json to be used to self-update."
        @Suppress("LeakingThis")
        manifestName.convention("manifest.json")
    }
}