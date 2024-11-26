package me.tatarka.android.selfupdate.gradle

import com.android.bundle.Devices
import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.commands.DumpCommand
import com.android.tools.build.bundletool.model.Password
import com.android.tools.build.bundletool.model.SigningConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.security.KeyStore

@DisableCachingByDefault
abstract class ExtractApkSplits : DefaultTask() {
    @get:InputFile
    abstract val appBundle: RegularFileProperty

    @get:InputFile
    abstract val aapt2: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val includeUniversal: Property<Boolean>

    @get:InputFile
    @get:Optional
    abstract val keystore: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val keystoreAlias: Property<String>

    @get:Input
    @get:Optional
    abstract val keystorePassword: Property<String>

    @get:Input
    @get:Optional
    abstract val keyPassword: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:OutputFile
    abstract val version: RegularFileProperty

    @TaskAction
    fun run() {
        logging.captureStandardOutput(LogLevel.INFO)
        val bundlePath = appBundle.get().asFile.toPath()
        val output = output.get().asFile
        output.listFiles()?.let { file -> file.forEach { it.deleteRecursively() } }

        val versionNameStream = ByteArrayOutputStream()
        val versionCodeStream = ByteArrayOutputStream()
        val minSdkStream = ByteArrayOutputStream()
        val maxSdkStream = ByteArrayOutputStream()

        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpCommand.DumpTarget.MANIFEST)
            .setXPathExpression("/manifest/@android:versionName")
            .setOutputStream(PrintStream(versionNameStream))
            .build()
            .execute()

        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpCommand.DumpTarget.MANIFEST)
            .setXPathExpression("/manifest/@android:versionCode")
            .setOutputStream(PrintStream(versionCodeStream))
            .build()
            .execute()

        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpCommand.DumpTarget.MANIFEST)
            .setXPathExpression("/manifest/uses-sdk/@android:minSdkVersion")
            .setOutputStream(PrintStream(minSdkStream))
            .build()
            .execute()

        DumpCommand.builder()
            .setBundlePath(bundlePath)
            .setDumpTarget(DumpCommand.DumpTarget.MANIFEST)
            .setXPathExpression("/manifest/uses-sdk/@android:maxSdkVersion")
            .setOutputStream(PrintStream(maxSdkStream))
            .build()
            .execute()

        val aapt2Command = aapt2.orNull?.asFile?.toPath()?.let { aapt2Path ->
            Aapt2Command.createFromExecutablePath(aapt2Path)
        }
        val signingConfiguration = keystore.orNull?.asFile?.toPath()?.let { keystorePath ->
            SigningConfiguration.extractFromKeystore(
                keystorePath,
                keystoreAlias.get(),
                java.util.Optional.ofNullable(
                    keystorePassword.orNull?.let {
                        Password { KeyStore.PasswordProtection(it.toCharArray()) }
                    }
                ),
                java.util.Optional.ofNullable(
                    keyPassword.orNull?.let {
                        Password { KeyStore.PasswordProtection(it.toCharArray()) }
                    }
                ),
            )
        }

        if (includeUniversal.getOrElse(false)) {
            BuildApksCommand.builder()
                .setApkBuildMode(BuildApksCommand.ApkBuildMode.UNIVERSAL)
                .setBundlePath(bundlePath)
                .setOutputFile(output.toPath())
                .setOutputPrintStream(System.out)
                .setOutputFormat(BuildApksCommand.OutputFormat.DIRECTORY)
                .apply {
                    if (aapt2Command != null) {
                        setAapt2Command(aapt2Command)
                    }
                    if (signingConfiguration != null) {
                        setSigningConfiguration(signingConfiguration)
                    }
                }
                .build()
                .execute()
        }

        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(output.toPath())
            .setOutputPrintStream(System.out)
            .setOutputFormat(BuildApksCommand.OutputFormat.DIRECTORY)
            .apply {
                if (aapt2Command != null) {
                    setAapt2Command(aapt2Command)
                }
                if (signingConfiguration != null) {
                    setSigningConfiguration(signingConfiguration)
                }
            }
            .build()
            .execute()

        val manifestVersion = ManifestVersion(
            name = versionNameStream.toString(Charsets.UTF_8).trimEnd(),
            code = versionCodeStream.toString(Charsets.UTF_8).trimEnd().toLong(),
            minSdk = minSdkStream.toString(Charsets.UTF_8).trimEnd().toIntOrNull(),
            maxSdk = maxSdkStream.toString(Charsets.UTF_8).trimEnd().toIntOrNull()
        )

        version.get().asFile.bufferedWriter().use {
            manifestVersion.write(it)
        }
    }
}