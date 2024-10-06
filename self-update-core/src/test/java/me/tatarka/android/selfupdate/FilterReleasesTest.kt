package me.tatarka.android.selfupdate

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.prop
import assertk.assertions.single
import me.tatarka.android.selfupdate.manifest.Manifest
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test

class FilterReleasesTest {
    private val manifestUrl = "https://example.com".toHttpUrl()

    @Test
    fun includes_all() {
        val releases = listOf(
            Manifest.Release(
                version_name = "1.0",
                version_code = 1,
                minSdk = 21,
                maxSdk = 34,
                artifacts = emptyList(),
            ),
            Manifest.Release(
                version_name = "2.0",
                version_code = 2,
                minSdk = 21,
                maxSdk = 34,
                artifacts = emptyList(),
            ),
        )

        val result = filterReleases(
            manifestUrl = manifestUrl,
            releases = releases,
            versionCode = 1,
            onlyUpgrades = false,
        )

        assertThat(result).containsExactly(
            SelfUpdate.Release(
                versionName = "1.0",
                versionCode = 1,
                manifestUrl = manifestUrl,
                artifacts = emptyList(),
            ),
            SelfUpdate.Release(
                versionName = "2.0",
                versionCode = 2,
                manifestUrl = manifestUrl,
                artifacts = emptyList(),
            )
        )
    }

    @Test
    fun includes_only_updates() {
        val releases = listOf(
            Manifest.Release(
                version_name = "1.0",
                version_code = 1,
                minSdk = 21,
                maxSdk = 34,
                artifacts = emptyList(),
            ),
            Manifest.Release(
                version_name = "2.0",
                version_code = 2,
                minSdk = 21,
                maxSdk = 34,
                artifacts = emptyList(),
            ),
        )

        val result = filterReleases(
            manifestUrl = manifestUrl,
            releases = releases,
            versionCode = 1,
            onlyUpgrades = true,
        )

        assertThat(result).containsExactly(
            SelfUpdate.Release(
                versionName = "2.0",
                versionCode = 2,
                manifestUrl = manifestUrl,
                artifacts = emptyList(),
            )
        )
    }

    @Test
    fun keeps_base_artifact() {
        val releases = listOf(
            Manifest.Release(
                version_name = "1.0",
                version_code = 1,
                minSdk = 21,
                maxSdk = 34,
                artifacts = listOf(
                    Manifest.Artifact(path = "base.apk"),
                    Manifest.Artifact(path = "x86.apk", abi = "x86")
                ),
            ),
        )

        val result = filterReleases(
            manifestUrl = manifestUrl,
            releases = releases,
            versionCode = 1,
            deviceInfo = DeviceInfo(
                sdk = 21,
                abis = arrayOf("armv7"),
                densityDpi = 0,
                languages = listOf("en_US")
            ),
            onlyUpgrades = false,
        )

        assertThat(result).single()
            .prop(SelfUpdate.Release::artifacts)
            .extracting(Manifest.Artifact::path)
            .containsExactly("base.apk")
    }

    @Test
    fun keeps_most_compatible_abi() {
        val releases = listOf(
            Manifest.Release(
                version_name = "1.0",
                version_code = 1,
                minSdk = 21,
                maxSdk = 34,
                artifacts = listOf(
                    Manifest.Artifact(path = "x86.apk", abi = "x86"),
                    Manifest.Artifact(path = "armeabi-v7a.apk", abi = "armeabi-v7a"),
                    Manifest.Artifact(path = "arm64-v8a.apk", abi = "arm64-v8a"),
                ),
            ),
        )

        val result = filterReleases(
            manifestUrl = manifestUrl,
            releases = releases,
            versionCode = 1,
            deviceInfo = DeviceInfo(
                sdk = 21,
                abis = arrayOf("arm64-v8a", "armeabi-v7a"),
                densityDpi = 0,
                languages = listOf("en_US")
            ),
            onlyUpgrades = false,
        )

        assertThat(result).single()
            .prop(SelfUpdate.Release::artifacts)
            .extracting(Manifest.Artifact::path)
            .containsExactly("arm64-v8a.apk")
    }

    @Test
    fun keeps_next_most_compatible_abi() {
        val releases = listOf(
            Manifest.Release(
                version_name = "1.0",
                version_code = 1,
                minSdk = 21,
                maxSdk = 34,
                artifacts = listOf(
                    Manifest.Artifact(path = "x86.apk", abi = "x86"),
                    Manifest.Artifact(path = "armeabi-v7a.apk", abi = "armeabi-v7a"),
                ),
            ),
        )

        val result = filterReleases(
            manifestUrl = manifestUrl,
            releases = releases,
            versionCode = 1,
            deviceInfo = DeviceInfo(
                sdk = 21,
                abis = arrayOf("arm64-v8a", "armeabi-v7a"),
                densityDpi = 0,
                languages = listOf("en_US")
            ),
            onlyUpgrades = false,
        )

        assertThat(result).single()
            .prop(SelfUpdate.Release::artifacts)
            .extracting(Manifest.Artifact::path)
            .containsExactly("armeabi-v7a.apk")
    }

    @Test
    fun keeps_the_highest_compatible_min_sdk_versions() {
        val releases = listOf(
            Manifest.Release(
                version_name = "1.0",
                version_code = 1,
                minSdk = 21,
                maxSdk = 34,
                artifacts = listOf(
                    Manifest.Artifact(
                        path = "base.apk",
                        minSdk = 21
                    ),
                    Manifest.Artifact(
                        path = "base_2.apk",
                        minSdk = 23
                    ),
                    Manifest.Artifact(
                        path = "base_3.apk",
                        minSdk = 29
                    ),
                    Manifest.Artifact(
                        path = "armeabi-v7a.apk",
                        abi = "armeabi-v7a",
                        minSdk = 21
                    ),
                    Manifest.Artifact(
                        path = "armeabi-v7a_2.apk",
                        abi = "armeabi-v7a",
                        minSdk = 23
                    )
                ),
            ),
        )

        val result = filterReleases(
            manifestUrl = manifestUrl,
            releases = releases,
            versionCode = 1,
            deviceInfo = DeviceInfo(
                sdk = 33,
                abis = arrayOf("armeabi-v7a"),
                densityDpi = 0,
                languages = listOf("en_US")
            ),
            onlyUpgrades = false,
        )

        assertThat(result).single()
            .prop(SelfUpdate.Release::artifacts)
            .extracting(Manifest.Artifact::path)
            .containsExactly(
                "base_3.apk",
                "armeabi-v7a_2.apk"
            )
    }
}