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
        targetSdk = 35
        // CI passes -PbuildNumber=<run number>; the in-app updater compares it with the latest release.
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        // GitHub repository whose releases carry new prototype APKs. Empty = no update checks.
        buildConfigField("String", "UPDATE_REPO", "\"${project.findProperty("updateRepo") ?: ""}\"")
        // AI gateway (Supabase Edge Function). Empty = the app uses the on-phone demo brain.
        // Set in ~/.gradle/gradle.properties or with -PparhlouAiUrl=... -PsupabaseAnonKey=...
        // Never put an OpenAI key here: it lives only on the server.
        buildConfigField("String", "PARHLOU_AI_URL", "\"${project.findProperty("parhlouAiUrl") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("supabaseAnonKey") ?: ""}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The scanner's OpenCV library is native: ship it for 64-bit and 32-bit ARM phones only.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Shared prototype key (android/keys/README.md) so every build can update the previous one. Not for the Play Store.
        create("prototype") {
            storeFile = rootProject.file("keys/prototype.jks")
            storePassword = "parhlou-prototype"
            keyAlias = "parhlou"
            keyPassword = "parhlou-prototype"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("prototype") }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
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
            // -PscreenshotDir=/path renders design screenshots (ScreenshotTest); skipped otherwise.
            project.findProperty("screenshotDir")?.let { test.systemProperty("parhlou.screenshotDir", it.toString()) }
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
