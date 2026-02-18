/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris instrumentation coverage for launch, registration readiness, and stream capture.

package li.crescio.penates.iris

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class InstrumentationTest {

  companion object {
    private const val TAG = "InstrumentationTest"
  }

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setup() = grantPermissions("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_CONNECT", "android.permission.INTERNET")

  @After
  fun tearDown() {
    MockDeviceKit.getInstance(context).reset()
  }

  @Test
  fun showsHomeScreenOnLaunch() {
    composeRule.waitUntilExactlyOneExists(hasText(context.getString(R.string.home_tip_video)), 5_000)
  }

  @Test
  fun showsNonStreamScreenWhenMockPaired() {
    MockDeviceKit.getInstance(context).pairRaybanMeta().powerOn()
    composeRule.waitUntilExactlyOneExists(
        hasText(context.getString(R.string.non_stream_screen_description)),
        5_000,
    )
  }

  @Test
  fun startThenStopStreaming() {
    MockDeviceKit.getInstance(context).pairRaybanMeta().apply {
      powerOn()
      don()
      getCameraKit().setCameraFeed(getFileUri("plant.mp4"))
      getCameraKit().setCapturedImage(getFileUri("plant.png"))
    }

    composeRule.onNodeWithText(context.getString(R.string.stream_button_title)).performClick()
    composeRule.waitUntilExactlyOneExists(
        hasContentDescription(context.getString(R.string.captured_photo)),
        15_000,
    )

    composeRule.onNodeWithContentDescription(context.getString(R.string.capture_photo)).performClick()
    composeRule.waitUntilExactlyOneExists(
        hasContentDescription(context.getString(R.string.captured_photo)),
        15_000,
    )
  }

  private fun grantPermissions(vararg permissions: String) {
    permissions.forEach { permission ->
      try {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
        Log.d(TAG, "Granted permission: $permission")
      } catch (error: IOException) {
        Log.e(TAG, "Failed to grant permission: $permission", error)
      }
    }
  }

  private fun getFileUri(assetName: String): Uri {
    val targetFile = File(context.cacheDir, assetName)
    InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
      FileOutputStream(targetFile).use(input::copyTo)
    }
    return Uri.fromFile(targetFile)
  }
}
