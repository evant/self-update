import com.android.build.gradle.internal.tasks.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory

abstract class PackageSelfUpdate : BaseTask() {
    @get:Input
    @get:Optional
    abstract val variantName: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty
    
    init {
        group = "Build"
        description = "Generates a set of apks and manifest.json to be used to self-update."
    }
}