/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: MainActivity wires permissions, SDK initialization, and root navigation.

package li.crescio.penates.iris

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.INTERNET
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.crescio.penates.iris.ui.CameraAccessScaffold
import li.crescio.penates.iris.wearables.WearablesViewModel

class MainActivity : ComponentActivity() {

  companion object {
    private val REQUIRED_PERMISSIONS = arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, INTERNET)
    private const val PERMISSIONS_ERROR_MESSAGE =
        "Allow all permissions (Bluetooth, Bluetooth Connect, Internet)"
  }

  private val wearablesViewModel: WearablesViewModel by viewModels()
  private val permissionRequestMutex = Mutex()
  private var pendingPermissionContinuation: CancellableContinuation<PermissionStatus>? = null

  private val androidPermissionsLauncher =
      registerForActivityResult(RequestMultiplePermissions(), ::onAndroidPermissionsResult)

  private val wearablesPermissionLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        pendingPermissionContinuation?.resume(result.getOrDefault(PermissionStatus.Denied))
        pendingPermissionContinuation = null
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    requestAndroidPermissionsAndBootstrap()
    handleIntent(intent)

    setContent {
      CameraAccessScaffold(
          viewModel = wearablesViewModel,
          onRequestWearablesPermission = ::requestWearablesPermission,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  suspend fun requestWearablesPermission(permission: Permission): PermissionStatus =
      permissionRequestMutex.withLock {
        suspendCancellableCoroutine { continuation ->
          pendingPermissionContinuation = continuation
          continuation.invokeOnCancellation { pendingPermissionContinuation = null }
          wearablesPermissionLauncher.launch(permission)
        }
      }

  private fun handleIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW) {
      wearablesViewModel.requestAutoStartStreaming()
    }
  }

  private fun requestAndroidPermissionsAndBootstrap() {
    androidPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
  }

  private fun onAndroidPermissionsResult(grants: Map<String, Boolean>) {
    if (grants.values.all { it }) {
      Wearables.initialize(this)
      wearablesViewModel.startMonitoring()
      return
    }
    wearablesViewModel.setRecentError(PERMISSIONS_ERROR_MESSAGE)
  }
}
