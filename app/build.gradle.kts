plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zappix.store"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zappix.store"
        minSdk = 23
        targetSdk = 35
        versionCode = 30
        versionName = "1.0.21"

        val apiBase = providers.gradleProperty("ZAPPIX_API_BASE_URL")
            .orElse("https://panelsandapps.com/panels/Zappix/api/")
            .get()
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")

        // Set to false (-PZAPPIX_ALLOW_HTTP_DOWNLOADS=false) once every catalog APK link is https.
        val allowHttpDownloads = providers.gradleProperty("ZAPPIX_ALLOW_HTTP_DOWNLOADS")
            .orElse("true")
            .get()
            .toBoolean()
        buildConfigField("boolean", "ALLOW_HTTP_DOWNLOADS", allowHttpDownloads.toString())
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("ZAPPIX_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("ZAPPIX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ZAPPIX_KEY_ALIAS")
                keyPassword = System.getenv("ZAPPIX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Test builds keep the production package name.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!System.getenv("ZAPPIX_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }

    // Robolectric layout tests inflate the real XML resources.
    testOptions { unitTests.isIncludeAndroidResources = true }

    lint {
        // Print the full report into the CI log.
        textReport = true
        textOutput = file("stdout")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}