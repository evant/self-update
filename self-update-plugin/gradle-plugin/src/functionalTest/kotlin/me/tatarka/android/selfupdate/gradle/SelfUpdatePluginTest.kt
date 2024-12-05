package me.tatarka.android.selfupdate.gradle

import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.android.AndroidManifest
import com.autonomousapps.kit.android.AndroidStyleRes
import com.autonomousapps.kit.gradle.BuildScript
import com.autonomousapps.kit.gradle.GradleProperties
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.PluginManagement
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import com.autonomousapps.kit.gradle.android.AndroidBlock
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class SelfUpdatePluginTest : AbstractGradleProject() {

    init {
        Files.copy(Paths.get("../../local.properties"), rootDir.resolve("local.properties"))
    }

    @Test
    fun smokeTest() {
        val project = createProject()
        val result = GradleBuilder.build(project.rootDir, "app:packageDebugSelfUpdate")

        assertThat(result.task(":app:packageDebugSelfUpdate"))
            .isNotNull()
            .prop(BuildTask::getOutcome)
            .isEqualTo(TaskOutcome.SUCCESS)

        assertThat(project.rootDir.resolve("app/build/outputs/selfupdate/debug/manifest.json")).exists()
    }

    private fun createProject(): GradleProject {
        return newGradleProjectBuilder()
            .withRootProject {
                gradleProperties = GradleProperties.minimalAndroidProperties()
                settingsScript.pluginManagement = PluginManagement(
                    Repositories(
                        Repository.FUNC_TEST_INCLUDED_BUILDS + Repository.DEFAULT
                    )
                )
            }
            .withAndroidSubproject("app") {
                withBuildScript {
                    plugins(
                        Plugin("com.android.application", "8.7.2"),
                        Plugin("me.tatarka.android.selfupdate", PLUGIN_UNDER_TEST_VERSION)
                    )
                    android = AndroidBlock.defaultAndroidAppBlock(namespace = "com.example")
                    manifest = AndroidManifest.appWithoutPackage()
                    styles = AndroidStyleRes.of(
                        """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources><style name="AppTheme"/></resources>
                    """.trimIndent()
                    )
                }
            }
            .write()
    }
}

//TODO: need support in testkit to pass additional content to the android block.

fun BuildScript.Builder.withSelfUpdate(builder: ProjectSelfUpdate.Builder.() -> Unit) = apply {
    val selfUpdate = ProjectSelfUpdate.Builder().apply(builder).build()
    withGroovy(
        """
        selfUpdate {
            mergeVariants = ${selfUpdate.mergeVariants} 
        }
        """.trimIndent()
    )
}

class ProjectSelfUpdate(
    val mergeVariants: Boolean = false
) {
    class Builder {
        var mergeVariants: Boolean = false

        fun build(): ProjectSelfUpdate {
            return ProjectSelfUpdate()
        }
    }
}