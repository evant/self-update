package me.tatarka.android.selfupdate.gradle

import org.gradle.api.provider.Property

interface GithubReleaseExtension {
    val owner: Property<String>
    val repo: Property<String>
    val tagName: Property<String>
    val name: Property<String>
    val body: Property<String>
    val prerelease: Property<Boolean>
    val token: Property<String>
}