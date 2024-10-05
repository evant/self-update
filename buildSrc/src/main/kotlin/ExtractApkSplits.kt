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

        BuildApksCommand.builder()
            .setBundlePath(bundlePath)
            .setOutputFile(output.toPath())
            .setOutputPrintStream(System.out)
            .setOutputFormat(BuildApksCommand.OutputFormat.DIRECTORY)
            .apply {
                val aapt2Path = aapt2.orNull?.asFile?.toPath()
                if (aapt2Path != null) {
                    setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2Path))
                }
                val keystorePath = keystore.orNull?.asFile?.toPath()
                if (keystorePath != null) {
                    setSigningConfiguration(
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
                    )
                }
            }
            .build()
            .execute()
        version.get().asFile.writeText(
            versionNameStream.toString(Charsets.UTF_8) +
                    versionCodeStream.toString(Charsets.UTF_8).trimEnd()
        )
    }
}