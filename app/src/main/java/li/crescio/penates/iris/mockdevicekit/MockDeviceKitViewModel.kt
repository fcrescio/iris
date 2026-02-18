/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: MockDeviceKitViewModel offers concise helpers for simulated device lifecycle actions.

package li.crescio.penates.iris.mockdevicekit

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockRaybanMeta
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MockDeviceKitViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "MockDeviceKitViewModel"
    private const val DEFAULT_DEVICE_NAME = "RayBan Meta Glasses"
  }

  private val mockDeviceKit = MockDeviceKit.getInstance(application.applicationContext)
  private val _uiState = MutableStateFlow(MockDeviceKitUiState())

  val uiState: StateFlow<MockDeviceKitUiState> = _uiState.asStateFlow()

  fun pairRaybanMeta() =
      runOperation("pair device") {
        val info =
            MockDeviceInfo(
                device = mockDeviceKit.pairRaybanMeta(),
                deviceId = UUID.randomUUID().toString(),
                deviceName = DEFAULT_DEVICE_NAME,
            )
        _uiState.update { it.copy(pairedDevices = it.pairedDevices + info) }
      }

  fun unpairDevice(deviceInfo: MockDeviceInfo) =
      runOperation("unpair device ${deviceInfo.deviceId}") {
        mockDeviceKit.unpairDevice(deviceInfo.device)
        _uiState.update { it.copy(pairedDevices = it.pairedDevices - deviceInfo) }
      }

  fun powerOn(deviceInfo: MockDeviceInfo) = operateDevice(deviceInfo, "power on") { it.powerOn() }

  fun powerOff(deviceInfo: MockDeviceInfo) =
      operateDevice(deviceInfo, "power off") { it.powerOff() }

  fun don(deviceInfo: MockDeviceInfo) = operateDevice(deviceInfo, "don") { it.don() }

  fun doff(deviceInfo: MockDeviceInfo) = operateDevice(deviceInfo, "doff") { it.doff() }

  fun fold(deviceInfo: MockDeviceInfo) = operateDevice(deviceInfo, "fold") { it.fold() }

  fun unfold(deviceInfo: MockDeviceInfo) = operateDevice(deviceInfo, "unfold") { it.unfold() }

  fun setCameraFeed(deviceInfo: MockDeviceInfo, uri: Uri) =
      runOperation("set camera feed ${deviceInfo.deviceId}") {
        deviceInfo.device.getCameraKit().setCameraFeed(uri)
        updateDevice(deviceInfo.copy(hasCameraFeed = true))
      }

  fun setCapturedImage(deviceInfo: MockDeviceInfo, uri: Uri) =
      runOperation("set captured image ${deviceInfo.deviceId}") {
        deviceInfo.device.getCameraKit().setCapturedImage(uri)
        updateDevice(deviceInfo.copy(hasCapturedImage = true))
      }

  private fun operateDevice(
      deviceInfo: MockDeviceInfo,
      action: String,
      operation: (MockRaybanMeta) -> Unit,
  ) = runOperation("$action ${deviceInfo.deviceId}") { operation(deviceInfo.device) }

  private fun updateDevice(updated: MockDeviceInfo) {
    _uiState.update { state ->
      state.copy(
          pairedDevices = state.pairedDevices.map { device -> if (device.deviceId == updated.deviceId) updated else device }
      )
    }
  }

  private fun runOperation(label: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      runCatching { block() }
          .onSuccess { Log.d(TAG, "Completed: $label") }
          .onFailure { Log.e(TAG, "Failed: $label", it) }
    }
  }
}
