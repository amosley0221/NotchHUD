import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Versioning
//
// versionName lives in android/version.properties. versionCode is derived from
// it so the two can never drift apart, and so a build is reproducible from a
// checkout alone (no dependence on CI run numbers, which reset if the repo moves).
// ---------------------------------------------------------------------------
val versionProps = Properties().apply {
    load(FileInputStream(rootProject.file("version.properties")))
}
val appVersionName: String = versionProps.getProperty("versionName")
val appVersionCode: Int = run {
    val parts = appVersionName.split(".").map { it.toIntOrNull() ?: 0 }
    require(parts.size == 3) { "versionName must be MAJOR.MINOR.PATCH, got '$appVersionName'" }
    val (major, minor, patch) = parts
    require(minor in 0..99 && patch in 0..99) { "minor and patch must each be 0-99" }
    major * 10_000 + minor * 100 + patch
}

// ---------------------------------------------------------------------------
// Release signing
//
// THE most important thing in this file. Android only allows an in-place update
// when the new APK is signed by the same certificate as the installed one. So the
// release key must be stable forever:
//   - CI reads it from repository secrets (base64 keystore + passwords).
//   - Locally you can drop a keystore.properties beside this module.
// If neither is present the release build falls back to the debug key, which is
// machine-specific; -PrequireReleaseSigning=true (what CI passes) turns that into
// a hard failure so we can never publish an unupgradeable APK by accident.
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val envStorePath: String? = System.getenv("NOTCHHUD_KEYSTORE_PATH")

val releaseSigning: Map<String, String>? = when {
    envStorePath != null && file(envStorePath).exists() -> mapOf(
        "storeFile" to envStorePath,
        "storePassword" to (System.getenv("NOTCHHUD_KEYSTORE_PASSWORD") ?: ""),
        "keyAlias" to (System.getenv("NOTCHHUD_KEY_ALIAS") ?: "notchhud"),
        "keyPassword" to (System.getenv("NOTCHHUD_KEY_PASSWORD") ?: ""),
    )
    keystorePropsFile.exists() -> {
        val p = Properties().apply { load(FileInputStream(keystorePropsFile)) }
        mapOf(
            "storeFile" to p.getProperty("storeFile"),
            "storePassword" to p.getProperty("storePassword"),
            "keyAlias" to p.getProperty("keyAlias"),
            "keyPassword" to p.getProperty("keyPassword"),
        )
    }
    else -> null
}

if (releaseSigning == null && project.findProperty("requireReleaseSigning") == "true") {
    throw GradleException(
        "Release signing config missing. Set NOTCHHUD_KEYSTORE_PATH / " +
            "NOTCHHUD_KEYSTORE_PASSWORD / NOTCHHUD_KEY_ALIAS / NOTCHHUD_KEY_PASSWORD, " +
            "or add android/keystore.properties. Publishing a debug-signed APK would " +
            "force users to uninstall before the next update."
    )
}

android {
    namespace = "com.notchhud.island"
    compileSdk = 35

    defaultConfig {
        // NEVER change applicationId. Android treats a different id as a different
        // app, so an id change means a side-by-side install, not an update.
        applicationId = "com.notchhud.island"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GIT_SHA", "\"${System.getenv("GITHUB_SHA") ?: "local"}\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning["storeFile"]!!)
                storePassword = releaseSigning["storePassword"]
                keyAlias = releaseSigning["keyAlias"]
                keyPassword = releaseSigning["keyPassword"]
                // Generated with openssl rather than keytool, so say so explicitly
                // instead of relying on AGP guessing the container format.
                storeType = "PKCS12"
                // v1 keeps older/sideload paths happy; v2+v3 are what modern Android
                // verifies. v3 is also what enables key rotation later without
                // breaking updates, so leave all of them on.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigning != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WARNING: no release keystore found — falling back to the debug key. " +
                        "APKs built this way cannot be used to update a properly signed install."
                )
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Distinct id so a debug build can sit beside a release install instead of
            // colliding with it (a debug-signed APK could never update it anyway).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    // One universal APK. Splitting by ABI would mean several APKs per release and a
    // versionCode scheme to match; not worth it for a pure-Kotlin app.
    splits {
        abi { isEnable = false }
        density { isEnable = false }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
}

// Emits the numbers the release workflow needs without re-parsing Gradle output.
tasks.register("printVersion") {
    doLast {
        println("versionName=$appVersionName")
        println("versionCode=$appVersionCode")
    }
}
