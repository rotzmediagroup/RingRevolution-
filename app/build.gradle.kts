import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Monetisation configuration: never hard-code production identifiers. Values come from (in order)
// environment variables, then secrets.properties (git-ignored), then the official Google TEST identifiers.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(name: String, default: String): String = System.getenv(name) ?: secrets.getProperty(name) ?: default
val admobAppId = cfg("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")          // Google sample app id
val adUnitRewarded = cfg("ADMOB_REWARDED_ID", "ca-app-pub-3940256099942544/5224354917")   // official test unit
val adUnitInterstitial = cfg("ADMOB_INTERSTITIAL_ID", "ca-app-pub-3940256099942544/1033173712")
val adUnitBanner = cfg("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/6300978111")
val adsEnabled = cfg("ADS_ENABLED", "false")
val iapEnabled = cfg("IAP_ENABLED", "false")
val premiumSku = cfg("PREMIUM_PRODUCT_ID", "dumpling_rings_premium")
val useTestAds = cfg("ADS_USE_TEST_IDS", "true")

android {
    namespace = "com.shiostudios.dumplingrings"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shiostudios.dumplingrings"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"
        vectorDrawables.useSupportLibrary = true
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled)
        buildConfigField("boolean", "ADS_USE_TEST_IDS", useTestAds)
        buildConfigField("boolean", "IAP_ENABLED", iapEnabled)
        buildConfigField("String", "AD_UNIT_REWARDED", "\"$adUnitRewarded\"")
        buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$adUnitInterstitial\"")
        buildConfigField("String", "AD_UNIT_BANNER", "\"$adUnitBanner\"")
        buildConfigField("String", "PREMIUM_PRODUCT_ID", "\"$premiumSku\"")
        resourceConfigurations += listOf("en", "nl", "de")
    }

    signingConfigs {
        // Release signing is injected from the environment; absent => the release build is unsigned (owner signs).
        create("release") {
            val ks = System.getenv("ANDROID_KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module") }
    androidResources { noCompress += listOf("webp", "ogg", "json") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}
