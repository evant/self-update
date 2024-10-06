package me.tatarka.android.selfupdate.gradle

import com.android.bundle.Commands.BuildApksResult
import com.android.bundle.Targeting
import com.android.bundle.Targeting.AbiTargeting
import com.android.bundle.Targeting.LanguageTargeting
import com.android.bundle.Targeting.ScreenDensityTargeting
import me.tatarka.android.selfupdate.manifest.Manifest
import java.io.File

internal fun parseArtifactMetadata(
    path: File,
    rename: (String) -> String = { it }
): List<Manifest.Artifact> {
    val toc = path.inputStream().buffered().use {
        BuildApksResult.parser().parseFrom(it)
    }
    return buildList {
        for (variant in toc.variantList) {
            val minSdk = variant.targeting.sdkVersionTargeting.valueList.firstOrNull()?.min?.value
            for (apkSet in variant.apkSetList) {
                for (description in apkSet.apkDescriptionList) {
                    val artifactPath = rename(description.path)
                    if (none { it.path == artifactPath }) {
                        add(
                            Manifest.Artifact(
                                path = artifactPath,
                                minSdk = minSdk,
                                abi = (description.targeting.abiTargeting
                                    ?: variant.targeting.abiTargeting)?.let { abiName(it) },
                                density = (description.targeting.screenDensityTargeting
                                    ?: variant.targeting.screenDensityTargeting)?.let { density(it) },
                                language = description.targeting.languageTargeting?.let { language(it) }
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun abiName(targeting: AbiTargeting): String? {
    return when (targeting.valueList.firstOrNull()?.alias) {
        Targeting.Abi.AbiAlias.ARMEABI -> "armabi"
        Targeting.Abi.AbiAlias.ARMEABI_V7A -> "armabi_v7a"
        Targeting.Abi.AbiAlias.ARM64_V8A -> "arm64_v8a"
        Targeting.Abi.AbiAlias.X86 -> "x86"
        Targeting.Abi.AbiAlias.X86_64 -> "x86_64"
        Targeting.Abi.AbiAlias.MIPS -> "mips"
        Targeting.Abi.AbiAlias.MIPS64 -> "mips64"
        Targeting.Abi.AbiAlias.RISCV64 -> "riscv64"
        Targeting.Abi.AbiAlias.UNRECOGNIZED -> throw IllegalArgumentException("Unrecognized abi")
        else -> null
    }
}

private fun density(targeting: ScreenDensityTargeting): String? {
    return when (targeting.valueList.firstOrNull()?.densityAlias) {
        Targeting.ScreenDensity.DensityAlias.NODPI -> "nodpi"
        Targeting.ScreenDensity.DensityAlias.LDPI -> "ldpi"
        Targeting.ScreenDensity.DensityAlias.MDPI -> "mdpi"
        Targeting.ScreenDensity.DensityAlias.TVDPI -> "tvdpi"
        Targeting.ScreenDensity.DensityAlias.HDPI -> "hdpi"
        Targeting.ScreenDensity.DensityAlias.XHDPI -> "xhdpi"
        Targeting.ScreenDensity.DensityAlias.XXHDPI -> "xxhdpi"
        Targeting.ScreenDensity.DensityAlias.XXXHDPI -> "xxxhdpi"
        Targeting.ScreenDensity.DensityAlias.UNRECOGNIZED -> throw IllegalArgumentException("Unrecognized density")
        else -> null
    }
}

private fun language(targeting: LanguageTargeting): String? {
    return targeting.valueList.firstOrNull()
}