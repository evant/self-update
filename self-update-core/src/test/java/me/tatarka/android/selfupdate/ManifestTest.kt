package me.tatarka.android.selfupdate

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import me.tatarka.android.selfupdate.Manifest.Artifact
import me.tatarka.android.selfupdate.Manifest.Release
import me.tatarka.android.selfupdate.Manifest.Updater
import kotlin.test.Test

class ManifestTest {
    @Test
    fun parses_manifest_json() {
        val manifest = Manifest.parse(
            """
            {
              "releases": [
                {
                  "version_name": "1.0",
                  "version_code": 1,
                  "minSdk": 21,
                  "maxSdk": 34,
                  "tags": [ "production" ],
                  "updater": {
                    "version": 2,
                    "feature_version": 1
                  },
                  "artifacts" : [
                    {
                      "path": "/build/outputs/apk/my-app.apk"
                    }
                  ]
                } 
              ]
            }
            """.trimIndent()
        )

        assertThat(manifest)
            .prop(Manifest::releases)
            .single()
            .all {
                prop(Release::version_name).isEqualTo("1.0")
                prop(Release::version_code).isEqualTo(1L)
                prop(Release::minSdk).isEqualTo(21)
                prop(Release::maxSdk).isEqualTo(34)
                prop(Release::tags).containsExactlyInAnyOrder("production")
                prop(Release::artifacts).single().all {
                    prop(Artifact::path).isEqualTo("/build/outputs/apk/my-app.apk")
                }
                prop(Release::updater).isNotNull().all {
                    prop(Updater::version).isEqualTo(2L)
                    prop(Updater::feature_version).isEqualTo(1L)
                }
            }
    }
}