plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.selfupdate.gradle)
}

android {
    namespace = "me.tatarka.android.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.tatarka.android.samplne"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations += listOf("en", "es")
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
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    bundle {
        density.enableSplit = false
        language.enableSplit = false
    }
}

dependencies {
    implementation(project(":self-update-core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.activity)
    implementation(libs.compose.viewmodel)
    implementation(libs.activity.ktx)
    debugImplementation(libs.compose.tooling.ui)
}