# Self Update

A system for self-updating your android app.

**Work in progress!** Not production-ready, apis may change in a backwards-incompatible way.

## Setup

### Gradle Plugin

Add the gradle plugin to you project
```kotlin
plugins {
    id("me.tatarka.android.selfupdate")
}
```
And run
```kotlin
./gradlew packageSelfUpdate
```

This plugin will generate a json manifest file to represent updates as well as extract the necessary
apk artifacts. You can find the output in `app/build/outputs/selfupdate/[debug/release]`. You can
host these files however you like, the app will just need the url to the manifest.json. We are going
to use python to start a simple web server for the remainder of this documentation.

```shell
cd app/build/outputs/selfupdate/debug
pyhton3 -m http.server
```

#### Manifest customization

You can set specific tags and release notes on the manifest either globally, or per-variant.

```kotlin
// build.gradle.kts
selfUpdate {
    notes = "Notes for this release"
}

android {
   buildTypes {
       debug {
           selfUpdate {
               // disable for debug builds
               enabled = false
           }
       }
       release {
           selfUpdate {
               // add a release tag
               tags = setOf("release")
           }
       }
   } 
}
```

#### Base Manifest

You can base your release off of an existing manifest, this allows you to keep a record of multiple
releases.

```kotlin
// build.gradle.kts
android {
    buildTypes {
        release {
            selfUpdate {
                base {
                    // Point to an existing manifest file to add too.
                    manifest = file("manifest.json")
                    // This will update the manifest with the current release
                    // when you run ./gradlew packageSelfUpdate
                    update = true
                }
            }
        }
    }
}
```

#### Merge manifests

You can merge all your variants into a single manifest. This allows you to have a single url to
fetch from. Each variant release will be tagged by that variant's name.

```kotlin
// build.gradle.kts
selfUpdate {
    mergeVariants = true
}
```

Note: The output will now be directly under `app/build/outputs/selfupdate` and no subfolders will be
created.

### Library

Add the library to your app
```kotlin
implementation("me.tatarka.android.selfupdate:self-update-core")
```

An update flow works as follows:

1. Fetch possible updates from your hosted manifest. You can then prompt the user to update to the 
   latest or present them a list to choose from.
   ```kotlin
   val selfUpdate = SelfUpdate(context)
   val releases = selfUpdate.check(
      manifestUrl = "http://10.0.0.18:8000/manifest.json",
      tags = setOf("release"), // optionally filter releases by one or more tags
      onlyUpgrades = true, // set false to return previous releases
   )
   ```
2. Optinally download a given release.
   ```kotlin
   try {
      selfUpdate.download(release, onProgress = { progress -> /* Download/Install progress */})
   } catch (e: IOException) {
      // failed to download, calling download again will resume where it left off
   }
   ```
4. Install a given release, will also download if not downloaded already.
   Note: it's likely this will prompt the user so you should consider when to do this.
   ```kotlin
   try {
      selfUpdate.install(release, onProgress = { progress -> /* Download/Install progress */})
   } catch (e: IOException) {
      // either failed to download or install
   }
   // on success it's likely your app process is killed so anything after this would be unreachable.
   ```

#### Listening for Updates

You can optionally listen for update broadcasts, for example, to show a notification to the user.

1. Create your broadcast receiver
   ```kotlin
   class MyUpdateReceiver : SelfUpdateReceiver() {
      override fun onUpdate(context: Context) {
          // show notification
      }
   }
   ```
2. Declare in your manifest, replacing the default one.
   ```xml
   <application>
       <receiver
           android:name="me.tatarka.android.selfupdate.SelfUpdateReceiver"
           tools:node="remove" />

       <receiver android:name=".MyUpdateReceiver">
           <meta-data
               android:name="me.tatarka.android.selfupdate.SelfUpdateReceiver"
               android:value="true" />
       </receiver>
   </application> 
   ```
3. Pass the class to the `SelfUpdate` constructor to make sure it's called.
   ```kotlin
   val selfUpdate = SelfUpdate(context, MyUpdateReceiver::class.java)
   ```

### Manifest Format

The manifest format is as follows. If you don't wish to use the gradle plugin you can write it by
hand or generate it using some other method.

```json5
{
   // A list of releases. These may be in any order but it's recommend you place the newer ones first.
   "releases": [
      {
         // The version name of the release (equivalent to android:versionName in AndroidManifest.xml)
         "version_name": "1.0",
         // The version code of the release (equivalent to android:versionCode in AndroidManifest.xml)
         "version_code": 1,
         // A list or arbitrary tags for the release, can be used for filtering. (optional)
         "tags": ["release"],
         // Notes for the release which may be shown to the user. (optional)
         "notes": "Release notes",
         // The minimum sdk version for the release. (optional)
         "minSdk": 21,
         // The maximum sdk version for the release. (optional)
         "maxSdk": 34,
         // Versioning metadata to ensure this release is compatible with the library that's 
         // performing the update.
         "updater": {
            // The version of self-update that this release was generated with.
            "version": 1,
            // The version of self-update that this release is compatible with.
            "feature_version": 1
         },
         // A list of artifacts for this release. A subset will be picked for a specific device.
         "artifacts": [
            {
               // The path to the artifact. Say you have this manifest hosted at
               // https://example.com/release/manifest.json
               // This can be:
               // 1. a relative path (base.apk -> https://example.com/release/base.apk)
               // 2. a domain-absolute path (/base.apk -> https://example.com/base.apk)
               // 3. a full url (https://cdn.example.com/base.apk)
               "path": "base.apk",
               // The minSdk for this artifact. This should be at least the release's minSdk. (optional)
               "minSdk": 21,
               // The abi for this artifact. (optional) 
               "abi": "armabi_v7a",
               // The density (dpi) for this artifact. (optional)
               "density": 320,
               // The language for this artifact. (optional)
               "language": "en"
            }
         ]
      }
   ]
}
```
