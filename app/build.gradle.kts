/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
  id("com.google.gms.google-services")
}

val githubActionVersionName = providers.gradleProperty("appVersionName").orNull
val defaultWebRtcAddress =
    providers.environmentVariable("DEFAULT_WEBRTC_ADDRESS").orElse("http://10.0.2.2:8080")
val defaultErmetePsk = providers.environmentVariable("DEFAULT_ERMETE_PSK").orElse("")

android {
  namespace = "li.crescio.penates.iris"
  compileSdk = 35

  buildFeatures { buildConfig = true }

  defaultConfig {
    applicationId = "li.crescio.penates.iris"
    minSdk = 31
    targetSdk = 34
    versionCode = 1
    versionName = githubActionVersionName ?: "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
    buildConfigField("String", "DEFAULT_WEBRTC_ADDRESS", "\"${defaultWebRtcAddress.get()}\"")
    buildConfigField("String", "DEFAULT_ERMETE_PSK", "\"${defaultErmetePsk.get()}\"")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.exifinterface)
  implementation(libs.okhttp)
  implementation(libs.webrtc)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.androidx.material3)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.camera)
  implementation(libs.mwdat.mockdevice)
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.test.rules)
}
