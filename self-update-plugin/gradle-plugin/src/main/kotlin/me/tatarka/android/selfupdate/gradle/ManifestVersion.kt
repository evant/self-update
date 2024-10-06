package me.tatarka.android.selfupdate.gradle

import java.io.Reader
import java.io.Serializable
import java.io.Writer

class ManifestVersion(
    val name: String,
    val code: Long,
    val minSdk: Int?,
    val maxSdk: Int?,
) : Serializable {

    companion object {
        fun parse(input: Reader): ManifestVersion {
            val (name, code, minSdk, maxSdk) = input.readLines()
            return ManifestVersion(
                name = name,
                code = code.toLong(),
                minSdk = minSdk.toIntOrNull(),
                maxSdk = maxSdk.toIntOrNull(),
            )
        }
    }

    fun write(output: Writer) {
        listOf(
            name,
            code,
            minSdk,
            maxSdk,
        ).forEach { output.appendLine(it?.toString() ?: "") }
    }
}