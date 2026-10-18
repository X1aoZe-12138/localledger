plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val ledgerDebugKeystore = rootProject.file(".tools/signing/local-ledger-debug.keystore")
val releaseStorePath = providers.environmentVariable("LOCALLEDGER_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("LOCALLEDGER_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LOCALLEDGER_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LOCALLEDGER_RELEASE_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() } && releaseStorePath?.let { file(it).isFile } == true
android {
    namespace = "cn.localledger"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.localledger"
        manifestPlaceholders["appLabel"] = "本地记账"
        minSdk = 31
        targetSdk = 35
        versionCode = 13
        versionName = "0.9.2-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug") {
            // Preserve the personal install's certificate locally; public clones use AGP's debug key.
            if (ledgerDebugKeystore.isFile) {
                storeFile = ledgerDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("communityRelease") {
            if (releaseSigningReady) {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            applicationIdSuffix = ".community"
            manifestPlaceholders["appLabel"] = "本地记账 · 社区测试版"
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("communityRelease")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("test").resources.srcDir("schemas")
}
val checkReleaseSigning by tasks.registering {
    group = "verification"
    description = "Rejects unsigned community release APKs."
    doLast {
        check(releaseSigningReady) {
            "Release signing is missing. Set the four LOCALLEDGER_RELEASE_* environment variables; see README.md."
        }
    }
}
tasks.matching { it.name == "packageRelease" }.configureEach { dependsOn(checkReleaseSigning) }
// Compose's test ComponentActivity comes from debug-only ui-test-manifest. Keep it out of
// the distributable APK; the full UI suite runs in debug, while release runs all non-UI tests.
tasks.withType<org.gradle.api.tasks.testing.Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
    exclude("**/ui/*UiTest.class", "**/ui/ScreenRenderTest.class")
}

// Keep AGP's app-debug.apk for tooling, and also produce an unambiguous file for installation.
// The version comes from defaultConfig, so future version bumps automatically change the name.
val installApkVersion = requireNotNull(android.defaultConfig.versionName)
    .replace(Regex("[^A-Za-z0-9]+"), "_")
    .trim('_')
val genericDebugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
val versionedDebugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug_${installApkVersion}.apk")
val copyVersionedDebugApk by tasks.registering {
    group = "build"
    description = "Copies the debug APK to a filename containing its version."
    dependsOn("packageDebug")
    inputs.file(genericDebugApk)
    outputs.file(versionedDebugApk)
    doLast {
        genericDebugApk.get().asFile.copyTo(versionedDebugApk.get().asFile, overwrite = true)
    }
}
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyVersionedDebugApk)
}
val genericReleaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
val versionedReleaseApk = layout.buildDirectory.file("outputs/apk/release/app-release_${installApkVersion}.apk")
val copyVersionedReleaseApk by tasks.registering {
    group = "build"
    description = "Copies the signed community APK to a versioned filename."
    dependsOn("packageRelease")
    inputs.file(genericReleaseApk)
    outputs.file(versionedReleaseApk)
    doLast { genericReleaseApk.get().asFile.copyTo(versionedReleaseApk.get().asFile, overwrite = true) }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyVersionedReleaseApk) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.datastore:datastore:1.1.7")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Opt in only when editing Android Studio previews. The install APK does not
    // need the ~4 MB inspection/preview runtime.
    if(providers.gradleProperty("composePreview").orNull == "true") {
        debugImplementation("androidx.compose.ui:ui-tooling")
    }
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
