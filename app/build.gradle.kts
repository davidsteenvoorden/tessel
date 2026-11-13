import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing lives outside the repo. Without it, release builds fall back to the debug key.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.david.tessel"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.david.tessel"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.5"
        resValue("string", "app_name", "Tessel")
    }

    signingConfigs {
        if (signing.isNotEmpty()) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // lets a debug build sit next to a release install on the same phone, under its own name
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Tessel Dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.play.services.nearby)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The README pictures are drawn by the game's own renderer on a computer, through
// Robolectric's real Android graphics. They only run when asked: ./gradlew shots
val shots = providers.gradleProperty("shots").isPresent ||
    gradle.startParameter.taskNames.any { it.endsWith("shots") }
tasks.withType<Test>().configureEach {
    if (!shots) exclude("**/Screenshots*")
    systemProperty("shots.dir", rootProject.file("docs").absolutePath)
}
tasks.register("shots") {
    dependsOn("testDebugUnitTest")
}

// ./gradlew distApk puts the signed release in dist/ as tesselv<version>.apk, ready to upload.
tasks.register<Copy>("distApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "tesselv${android.defaultConfig.versionName}.apk" }
}
