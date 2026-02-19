/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: WearablesViewModel centralizes registration, discovery, and streaming entry logic.

package li.crescio.penates.iris.wearables

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WearablesViewModel(application: Application) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(WearablesUiState())
  val uiState: StateFlow<WearablesUiState> = _uiState.asStateFlow()

  val deviceSelector: DeviceSelector = AutoDeviceSelector()

  private var isMonitoring = false
  private var activeDeviceJob: Job? = null
  private val deviceMetadataJobs = mutableMapOf<DeviceIdentifier, Job>()

  fun startMonitoring() {
    if (isMonitoring) return
    isMonitoring = true

    observeActiveDevice()
    observeRegistration()
    observeDevices()
  }

  fun startRegistration(activity: Activity) = Wearables.startRegistration(activity)

  fun startUnregistration(activity: Activity) = Wearables.startUnregistration(activity)

  fun navigateToStreaming(onRequestWearablesPermission: suspend (Permission) -> PermissionStatus) {
    viewModelScope.launch {
      val permission = Permission.CAMERA
      val permissionResult = Wearables.checkPermissionStatus(permission)

      permissionResult.onFailure { error, _ ->
        setRecentError("Permission check error: ${error.description}")
        return@launch
      }

      if (permissionResult.getOrNull() == PermissionStatus.Granted) {
        startStreaming()
        return@launch
      }

      when (onRequestWearablesPermission(permission)) {
        PermissionStatus.Granted -> startStreaming()
        PermissionStatus.Denied -> setRecentError("Permission denied")
      }
    }
  }


  fun navigateToDeviceSelection() = updateState { copy(isStreaming = false) }

  fun requestAutoStartStreaming() = updateState { copy(shouldAutoStartStreaming = true) }

  fun consumeAutoStartStreamingRequest() = updateState { copy(shouldAutoStartStreaming = false) }

  fun showDebugMenu() = updateState { copy(isDebugMenuVisible = true) }

  fun hideDebugMenu() = updateState { copy(isDebugMenuVisible = false) }

  fun clearCameraPermissionError() = updateState { copy(recentError = null) }

  fun setRecentError(error: String) = updateState { copy(recentError = error) }

  fun showGettingStartedSheet() = updateState { copy(isGettingStartedSheetVisible = true) }

  fun hideGettingStartedSheet() = updateState { copy(isGettingStartedSheetVisible = false) }

  fun setPhotoIntervalMs(intervalMs: Long) =
      updateState { copy(photoIntervalMs = intervalMs.coerceIn(5_000L, 20_000L)) }

  fun setServerHttpUrl(url: String) = updateState { copy(serverHttpUrl = url.trimEnd('/')) }

  private fun observeActiveDevice() {
    activeDeviceJob =
        viewModelScope.launch {
          deviceSelector.activeDevice(Wearables.devices).collect { activeDevice ->
            updateState { copy(hasActiveDevice = activeDevice != null) }
          }
        }
  }

  private fun observeRegistration() {
    viewModelScope.launch {
      Wearables.registrationState.collect { next ->
        val showSheet =
            next is RegistrationState.Registered &&
                _uiState.value.registrationState is RegistrationState.Registering
        updateState {
          copy(registrationState = next, isGettingStartedSheetVisible = showSheet)
        }
      }
    }
  }

  private fun observeDevices() {
    viewModelScope.launch {
      Wearables.devices.collect { devices ->
        updateState {
          copy(
              devices = devices.toList().toImmutableList(),
              hasMockDevices = MockDeviceKit.getInstance(getApplication()).pairedDevices.isNotEmpty(),
          )
        }
        refreshCompatibilityMonitoring(devices)
      }
    }
  }

  private fun refreshCompatibilityMonitoring(devices: Set<DeviceIdentifier>) {
    val removed = deviceMetadataJobs.keys - devices
    removed.forEach { id -> deviceMetadataJobs.remove(id)?.cancel() }

    (devices - deviceMetadataJobs.keys).forEach { id ->
      deviceMetadataJobs[id] =
          viewModelScope.launch {
            Wearables.devicesMetadata[id]?.collect { metadata ->
              if (metadata.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED) {
                val deviceName = metadata.name.ifEmpty { id }
                setRecentError("Device '$deviceName' requires an update to work with Iris")
              }
            }
          }
    }
  }

  private fun startStreaming() =
      updateState {
        copy(isStreaming = true, shouldAutoStartStreaming = false)
      }

  private inline fun updateState(update: WearablesUiState.() -> WearablesUiState) {
    _uiState.update(update)
  }

  override fun onCleared() {
    activeDeviceJob?.cancel()
    deviceMetadataJobs.values.forEach(Job::cancel)
    deviceMetadataJobs.clear()
    super.onCleared()
  }
}
