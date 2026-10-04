import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.play.publisher)
}

// Only apply google-services plugin when google-services.json is present
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSetting(name: String): String? =
    keystoreProperties.getProperty(name) ?: providers.gradleProperty("latr.$name").orNull

android {
    namespace = "io.hafa.latr"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.hafa.latr"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // storeFile, storePassword, keyAlias and keyPassword come from android/keystore.properties
    // (git-ignored), or from -Platr.<name> in CI. Without them release is debug-signed, which
    // installs locally but Play won't accept.
    val uploadStoreFile = signingSetting("storeFile")
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = signingSetting("storePassword")
                keyAlias = signingSetting("keyAlias")
                keyPassword = signingSetting("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(
                if (uploadStoreFile != null) "upload" else "debug"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    lint {
        warningsAsErrors = true
        // Version nags appear on their own as time passes; Dependabot handles them.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
    sourceSets {
        getByName("test") {
            // Shared with web: web/utils/*.spec.ts read the same fixture.
            resources.directories.add("../../testdata")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(true)
    }
}

// publishReleaseBundle uploads to Play; credentials come from ANDROID_PUBLISHER_CREDENTIALS (.github/workflows/cut-android.yml).
play {
    track.set("internal")
    defaultToAppBundles.set(true)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("app/compose_stability.conf")
    )
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Credential Manager (Google Sign-In)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.google.id)

    testImplementation(libs.junit)
    // JVM unit tests only get Android's stubbed org.json.
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
