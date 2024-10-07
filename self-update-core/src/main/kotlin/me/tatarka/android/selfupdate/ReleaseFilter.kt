package me.tatarka.android.selfupdate

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import okhttp3.HttpUrl
import java.util.Locale
import kotlin.math.abs

private const val ANY_DPI = 65534

internal class DeviceInfo(
    val sdk: Int,
    val abis: Array<String>,
    val densityDpi: Int,
    val languages: List<String>,
) {
    companion object {
        val Unknown = DeviceInfo(
            sdk = 0,
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
    val languages = res.configuration.locales().mapNotNull { it.language.ifEmpty { null } }
    return DeviceInfo(
        sdk = Build.VERSION.SDK_INT,
        abis = abis,
        densityDpi = densityDpi,
        languages = languages
    )
}

internal fun filterReleases(
    manifestUrl: HttpUrl,
    releases: List<me.tatarka.android.selfupdate.manifest.Manifest.Release>,
    versionCode: Long,
    deviceInfo: DeviceInfo = DeviceInfo.Unknown,
    tags: Set<String>? = null,
    onlyUpgrades: Boolean = true,
): List<SelfUpdate.Release> {
    return releases
        .filter {
            if (tags != null && it.tags != tags) return@filter false
            if (onlyUpgrades && it.version_code <= versionCode) return@filter false
            if (deviceInfo.sdk > 0 && !sdkInRange(deviceInfo.sdk, it.minSdk, it.maxSdk))
                return@filter false
            true
        }
        .map {
            SelfUpdate.Release(
                manifestUrl = manifestUrl,
                versionName = it.version_name,
                versionCode = it.version_code,
                notes = it.notes,
                tags = it.tags,
                artifacts = filterArtifacts(it.artifacts, deviceInfo),
            )
        }
}

private fun sdkInRange(sdk: Int, min: Int?, max: Int?): Boolean {
    return when {
        min != null && max != null -> sdk in min..max
        min != null -> sdk >= min
        max != null -> sdk <= max
        else -> true
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

private fun filterArtifacts(
    artifacts: List<me.tatarka.android.selfupdate.manifest.Manifest.Artifact>,
    deviceInfo: DeviceInfo,
): List<me.tatarka.android.selfupdate.manifest.Manifest.Artifact> {
    if (deviceInfo === DeviceInfo.Unknown) {
        return artifacts
    }
    val result = mutableListOf<me.tatarka.android.selfupdate.manifest.Manifest.Artifact>()
    var targetBaseSdk = 0
    var abiMatchIndex = Int.MAX_VALUE
    var targetAbiSdk = 0
    var targetDensity = 0
    for (artifact in artifacts) {
        val artifactMinSdk = artifact.minSdk
        val artifactAbi = artifact.abi
        val artifactDensity = artifact.density
        val artifactLanguage = artifact.language
        if (artifactAbi == null && artifactDensity == null && artifactLanguage == null) {
            // base artifact
            if (artifactMinSdk != null && artifactMinSdk <= deviceInfo.sdk && artifactMinSdk > targetBaseSdk) {
                targetBaseSdk = artifactMinSdk
            }
        } else if (artifactAbi != null) {
            // search for the abi that matches the lowest index in the list of compatible abi's
            val abiIndex = deviceInfo.abis.indexOf(artifactAbi)
            if (abiIndex != -1 && abiIndex <= abiMatchIndex) {
                abiMatchIndex = abiIndex
                if (artifactMinSdk != null && artifactMinSdk <= deviceInfo.sdk && artifactMinSdk > targetAbiSdk) {
                    targetAbiSdk = artifactMinSdk
                }
            }
        } else if (artifactDensity != null) {
            // find best matching density
            if (targetDensity == 0) {
                targetDensity = artifactDensity
            } else if (artifactDensity != targetDensity) {
                // ANY_DPI always wins
                if (targetDensity == ANY_DPI) {
                    continue
                }
                if (artifactDensity == ANY_DPI) {
                    targetDensity = artifactDensity
                    continue
                }
                // Picks which dpi matches better the desired dpi taking into account the scaling formula.
                val currentDensityDistance = densityDistance(deviceInfo.densityDpi, targetDensity)
                val artifactDensityDistance = densityDistance(deviceInfo.densityDpi, artifactDensity)
                if (artifactDensityDistance < currentDensityDistance) {
                    targetDensity = artifactDensity
                }
            }
        }
    }
    val targetAbi = deviceInfo.abis.getOrNull(abiMatchIndex)
    for (artifact in artifacts) {
        if (artifact.abi == null && artifact.density == null && artifact.language == null) {
            if (artifact.minSdk == null || artifact.minSdk == targetBaseSdk) {
                result.add(artifact)
            }
        }
        if (targetAbi != null) {
            if (artifact.abi == targetAbi && (artifact.minSdk == null || artifact.minSdk == targetAbiSdk)) {
                result.add(artifact)
            }
        }
        if (targetDensity != 0) {
            if (artifact.density == targetDensity) {
                result.add(artifact)
            }
        }
        if (artifact.language in deviceInfo.languages) {
            result.add(artifact)
        }
    }
    return result
}

// Scaling down is 2x better than up.
private fun densityDistance(target: Int, value: Int): Int {
    return abs(target - value) * if (target > value) 2 else 1
}