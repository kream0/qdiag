plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds get their version from the tag (v1.2.3 -> versionCode 1002003); test builds
// from branch pushes are "0.dev.<run>" with a small versionCode, so any release updates them.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val releaseVersionName = System.getenv("QDIAG_VERSION_NAME")?.takeIf { it.isNotBlank() }
val releaseVersionCode = System.getenv("QDIAG_VERSION_CODE")?.toIntOrNull()

// Where the in-app updater looks for new builds (the latest published release).
val updateRepo = System.getenv("GITHUB_REPOSITORY") ?: "kream0/qdiag"

// A private keystore can be supplied through env vars (see the CI workflow). Without one,
// builds are signed with the public dev key in /keystore so updates still install over each other.
val releaseKeystore = System.getenv("QDIAG_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "app.qdiag"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.qdiag"
        minSdk = 29
        targetSdk = 36
        versionCode = releaseVersionCode ?: runNumber
        versionName = releaseVersionName ?: "0.dev.$runNumber"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("sideload") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("QDIAG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QDIAG_KEY_ALIAS")
                keyPassword = System.getenv("QDIAG_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/qdiag-dev.jks")
                storePassword = "qdiag-dev"
                keyAlias = "qdiag"
                keyPassword = "qdiag-dev"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
