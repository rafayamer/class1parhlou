plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pk.parhlou.student"
    compileSdk = 36

    defaultConfig {
        applicationId = "pk.parhlou.student"
        minSdk = 25
        // Google Play requires new apps to target the newest Android (16, API 36) from 31 August 2026.
        targetSdk = 36
        // CI passes -PbuildNumber=<run number>; the in-app updater compares it with the latest release.
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        // AI gateway (Supabase Edge Function). Empty = the app uses the on-phone demo brain.
        // Set in ~/.gradle/gradle.properties or with -PparhlouAiUrl=... -PsupabaseAnonKey=...
        // Never put an OpenAI key here: it lives only on the server.
        buildConfigField("String", "PARHLOU_AI_URL", "\"${project.findProperty("parhlouAiUrl") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("supabaseAnonKey") ?: ""}\"")
        // AdMob (free plan only). Google's public test IDs until the founder's own AdMob app and ad unit are set:
        // -PadmobAppId=ca-app-pub-…~… -PadmobInterstitial=ca-app-pub-…/…
        manifestPlaceholders["admobAppId"] = project.findProperty("admobAppId") ?: "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_INTERSTITIAL", "\"${project.findProperty("admobInterstitial") ?: "ca-app-pub-3940256099942544/1033173712"}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The scanner's OpenCV library is native: ship it for 64-bit and 32-bit ARM phones only.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Two versions of the app:
    //  - prototype: the APK shared directly. Updates itself from GitHub releases; Plus is paid by JazzCash/Easypaisa transfer.
    //  - play: the Google Play version. No self-updater and no permission to install apps (Play doesn't allow either),
    //    no manual payments (Play requires Google Play Billing for Plus), and ads only once real AdMob IDs are given.
    flavorDimensions += "channel"
    productFlavors {
        create("prototype") {
            dimension = "channel"
            // GitHub repository whose releases carry new prototype APKs. Empty = no update checks.
            buildConfigField("String", "UPDATE_REPO", "\"${project.findProperty("updateRepo") ?: ""}\"")
            buildConfigField("boolean", "MANUAL_PAYMENTS", "true")
            buildConfigField("boolean", "ADS", "true")
        }
        create("play") {
            dimension = "channel"
            buildConfigField("String", "UPDATE_REPO", "\"\"")
            buildConfigField("boolean", "MANUAL_PAYMENTS", "false")
            buildConfigField("boolean", "ADS", "${project.findProperty("admobInterstitial") != null}")
        }
    }

    signingConfigs {
        // Shared prototype key (android/keys/README.md) so every build can update the previous one. Not for the Play Store.
        create("prototype") {
            storeFile = rootProject.file("keys/prototype.jks")
            storePassword = "parhlou-prototype"
            keyAlias = "parhlou"
            keyPassword = "parhlou-prototype"
        }
        // The private release key (Play Store upload key). The prototype key above is public in this repository, so any
        // build for real students must use this one. CI writes it from the RELEASE_KEYSTORE_B64 secret; never commit it.
        System.getenv("RELEASE_KEYSTORE")?.takeIf { file(it).isFile }?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("prototype") }
        // The APK students install: optimised (R8) and signed with the same prototype key, so it updates any earlier build.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("prototype")
        }
    }
    compileOptions {
        // java.time (dates, streaks, the calendar) only exists from Android 8; this adds it for Android 7.x phones.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // Compress native libraries (mostly OpenCV) in the APK: a much smaller download on mobile data, unpacked at install.
    packaging { jniLibs { useLegacyPackaging = true } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Offline Robolectric: pass -ProbolectricDir=/path/with/android-all-instrumented jars
        // where the network blocks Robolectric's own downloader.
        unitTests.all { test ->
            // Robolectric renders whole screens; the default 512 MB runs out across the full suite.
            test.maxHeapSize = "2g"
            // Robolectric holds on to memory across test classes; a fresh process every 15 keeps late tests from slowing to a crawl.
            test.setForkEvery(15)
            // -PscreenshotDir=/path renders design screenshots (ScreenshotTest); skipped otherwise.
            project.findProperty("screenshotDir")?.let { test.systemProperty("parhlou.screenshotDir", it.toString()) }
            project.findProperty("iconOut")?.let { test.systemProperty("parhlou.iconOut", it.toString()) }
            // -PcontentExport=/path/sample.json writes the sample content for the AI gateway (ContentExportTest).
            project.findProperty("contentExport")?.let { test.systemProperty("parhlou.contentExport", it.toString()) }
            // -PcourseBundle=/path/bundle.json checks a built course with the app's own marker (ContentQualityTest).
            project.findProperty("courseBundle")?.let { test.systemProperty("parhlou.courseBundle", it.toString()) }
            project.findProperty("robolectricDir")?.let { dir ->
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", dir.toString())
            }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation(composeBom)
    implementation(project(":prismal"))
    implementation(project(":scanner"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
