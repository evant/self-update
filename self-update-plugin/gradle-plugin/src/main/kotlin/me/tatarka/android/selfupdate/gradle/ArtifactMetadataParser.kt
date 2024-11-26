package me.tatarka.android.selfupdate.gradle

import com.android.apksig.ApkVerifier
import com.android.apksig.internal.apk.ApkSigningBlockUtils
import com.android.apksig.internal.apk.ContentDigestAlgorithm
import com.android.bundle.Commands.BuildApksResult
import com.android.bundle.Targeting
import com.android.bundle.Targeting.AbiTargeting
import com.android.bundle.Targeting.LanguageTargeting
import com.android.bundle.Targeting.ScreenDensityTargeting
import com.android.tools.build.bundletool.model.utils.ResourcesUtils
import me.tatarka.android.selfupdate.manifest.Manifest
import java.io.File
import java.util.Base64

internal fun parseArtifactMetadata(
    path: File,
    universal: File?,
    rename: (String) -> String = { it }
): List<Manifest.Artifact> {
    val toc = path.inputStream().buffered().use {
        BuildApksResult.parser().parseFrom(it)
    }
    return buildList {
        if (universal != null) {
            val checksums = checksums(universal)
            add(
                Manifest.Artifact(
                    path = rename(universal.relativeTo(path.parentFile).path),
                    universal = true,
                    checksums = checksums.ifEmpty { null },
                )
            )
        }
        for (variant in toc.variantList) {
            val minSdk = variant.targeting.sdkVersionTargeting.valueList.firstOrNull()?.min?.value
            for (apkSet in variant.apkSetList) {
                for (description in apkSet.apkDescriptionList) {
                    val artifactPath = rename(description.path)
                    if (none { it.path == artifactPath }) {
                        val apkFile = path.parentFile.resolve(description.path)
                        val checksums = checksums(apkFile)
                        add(
                            Manifest.Artifact(
                                path = artifactPath,
                                minSdk = minSdk,
                                abi = (description.targeting.abiTargeting
                                    ?: variant.targeting.abiTargeting)?.let { abiName(it) },
                                density = (description.targeting.screenDensityTargeting
                                    ?: variant.targeting.screenDensityTargeting)?.let { density(it) },
                                language = description.targeting.languageTargeting?.let {
                                    language(
                                        it
                                    )
                                },
                                checksums = checksums.ifEmpty { null },
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun checksums(path: File): List<String> {
    val result = ApkVerifier.Builder(path).build().verify()
    val digests = ApkVerifier.getContentDigestsFromResult(
        result,
        ApkSigningBlockUtils.VERSION_APK_SIGNATURE_SCHEME_V2
    )
    val base64 = Base64.getUrlEncoder().withoutPadding()
    return digests.mapNotNull { (algorithm, data) ->
        val prefix = when (algorithm) {
            ContentDigestAlgorithm.CHUNKED_SHA256 -> "v2:sha256:"
            ContentDigestAlgorithm.CHUNKED_SHA512 -> "v2:sha512:"
            else -> return@mapNotNull null
        }
        prefix + base64.encodeToString(data)
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

private fun density(targeting: ScreenDensityTargeting): Int? {
    return targeting.valueList.firstOrNull()?.let {
        ResourcesUtils.convertToDpi(it)
    }
}

private fun language(targeting: LanguageTargeting): String? {
    return targeting.valueList.firstOrNull()?.let {
        ResourcesUtils.convertLocaleToLanguage(it)
    }
}