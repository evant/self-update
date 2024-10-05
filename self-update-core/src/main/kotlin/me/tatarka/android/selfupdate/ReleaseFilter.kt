package me.tatarka.android.selfupdate

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import okhttp3.HttpUrl
import okhttp3.internal.indexOf
import java.util.Locale

internal class DeviceInfo(
    val abis: Array<String>,
    val densityDpi: Int,
    val languages: List<String>,
) {
    companion object {
        val Unknown = DeviceInfo(
            abis = emptyArray(),
            densityDpi = 0,
            languages = emptyList(),
        )
    }
}

internal fun DeviceInfo(context: Context): DeviceInfo {
    val abis = Build.SUPPORTED_ABIS
    val res = context.resources
    val densityDpi = res.displayMetrics.densityDpi
    val languages = res.configuration.locales().mapNotNull { it.isO3Language.ifEmpty { null } }
    return DeviceInfo(abis = abis, densityDpi = densityDpi, languages = languages)
}

internal fun filterReleases(
    manifestUrl: HttpUrl,
    releases: List<Manifest.Release>,
    versionCode: Long,
    deviceInfo: DeviceInfo = DeviceInfo.Unknown,
    tags: Set<String>? = null,
    onlyUpgrades: Boolean = true,
): List<SelfUpdate.Release> {
    return releases
        .filter {
            if (tags != null && it.tags != tags) return@filter false
            if (onlyUpgrades && it.version_code <= versionCode) return@filter false
            true
        }
        .map {
            SelfUpdate.Release(
                manifestUrl = manifestUrl,
                versionName = it.version_name,
                versionCode = it.version_code,
                artifacts = filterSplits(it.artifacts, deviceInfo),
            )
        }
}

private fun Configuration.locales(): List<Locale> {
    return if (Build.VERSION.SDK_INT >= 24) {
        val list = locales
        List(list.size()) { list[it] }
    } else {
        listOf(locale)
    }
}

private fun filterSplits(
    artifacts: List<Manifest.Artifact>,
    deviceInfo: DeviceInfo,
): List<Manifest.Artifact> {
    if (deviceInfo === DeviceInfo.Unknown) {
        return artifacts
    }
    val result = mutableListOf<Manifest.Artifact>()
    var abiMatchIndex = Int.MAX_VALUE
    var abiArtifactIndex = -1
    artifacts.forEachIndexed { index, artifact ->
        if (artifact.abi == null && artifact.density == null && artifact.language == null) {
            // base artifact
            result.add(artifact)
        } else if (artifact.abi != null) {
            // search for the abi that matches the lowest index in the list of compatible abi's
            val abiIndex = deviceInfo.abis.indexOf(artifact.abi)
            if (abiIndex != -1 && abiIndex < abiMatchIndex) {
                abiMatchIndex = abiIndex
                abiArtifactIndex = index
            }
        }
    }
    if (abiArtifactIndex != -1) {
        result.add(artifacts[abiArtifactIndex])
    }
    return result
}
