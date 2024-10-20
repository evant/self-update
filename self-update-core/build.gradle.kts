plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.conventions.publish)
}

android {
    namespace = "me.tatarka.android.selfupdate"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        managedDevices {
            localDevices {
                create("api34") {
                    device = "Pixel 5"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
                create("api27") {
                    device = "Pixel 5"
                    apiLevel = 27
                    systemImageSource = "aosp"
                }
                create("api21") {
                    device = "Pixel 5"
                    apiLevel = 21
                    systemImageSource = "aosp"
                }
            }
            groups {
                create("all") {
                    targetDevices.addAll(devices)
                }
            }
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.selfupdate.manifest)
    api(libs.kotlinx.coroutine)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.await)
    implementation(libs.annotations)
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.android.test)
}

publishing {
    publications {
        afterEvaluate {
            named<MavenPublication>("release").configure {
                from(components["release"])
            }
        }
    }
}