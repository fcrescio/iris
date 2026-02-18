/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: MockDeviceKitUiState models simulator devices and configured media fixtures.

package li.crescio.penates.iris.mockdevicekit

import com.meta.wearable.dat.mockdevice.api.MockRaybanMeta

data class MockDeviceInfo(
    val device: MockRaybanMeta,
    val deviceId: String,
    val deviceName: String,
    val hasCameraFeed: Boolean = false,
    val hasCapturedImage: Boolean = false,
)

data class MockDeviceKitUiState(val pairedDevices: List<MockDeviceInfo> = emptyList())
