import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val uploadSigningEnvironment =
    mapOf(
        "storeFile" to System.getenv("TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE"),
        "storePassword" to System.getenv("TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_PASSWORD"),
        "keyAlias" to System.getenv("TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS"),
        "keyPassword" to System.getenv("TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_PASSWORD"),
    )
val uploadSigningConfigured = uploadSigningEnvironment.values.all { !it.isNullOrBlank() }
val uploadSigningPartiallyConfigured =
    uploadSigningEnvironment.values.any { !it.isNullOrBlank() } && !uploadSigningConfigured
val allowUnsignedRelease =
    System.getenv("TRAFFIC_MONITORING_ALLOW_UNSIGNED_RELEASE").equals("true", ignoreCase = true)

gradle.taskGraph.whenReady {
    val packagesRelease =
        allTasks.any { task ->
            task.path == ":app:bundleRelease" || task.path == ":app:assembleRelease"
        }

    if (uploadSigningPartiallyConfigured) {
        throw GradleException(
            "Release signing is incomplete. Set all TRAFFIC_MONITORING_ANDROID_UPLOAD_* variables; " +
                "never commit upload-key material.",
        )
    }

    if (packagesRelease && !uploadSigningConfigured && !allowUnsignedRelease) {
        throw GradleException(
            "Release signing is not configured. Use scripts/build-play-release.sh build, " +
                "or set TRAFFIC_MONITORING_ALLOW_UNSIGNED_RELEASE=true only for an intentional unsigned CI artifact.",
        )
    }
}

val versionPropertiesFile = file("version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.exists()) {
        FileInputStream(versionPropertiesFile).use { load(it) }
    }
}
val currentVersionCode = (versionProperties.getProperty("versionCode") ?: "1").toInt()
val currentVersionName = versionProperties.getProperty("versionName") ?: "0.1.0-m1a"

android {
    namespace = "com.daniele21.trafficmonitoring"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daniele21.trafficmonitoring"
        minSdk = 26
        targetSdk = 35
        versionCode = currentVersionCode
        versionName = currentVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("upload") {
            if (uploadSigningConfigured) {
                storeFile = file(uploadSigningEnvironment.getValue("storeFile")!!)
                storePassword = uploadSigningEnvironment.getValue("storePassword")
                keyAlias = uploadSigningEnvironment.getValue("keyAlias")
                keyPassword = uploadSigningEnvironment.getValue("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
