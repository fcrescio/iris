/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: NonStreamScreen handles post-registration readiness and stream launch.

package li.crescio.penates.iris.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.launch
import li.crescio.penates.iris.R
import li.crescio.penates.iris.wearables.WearablesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonStreamScreen(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val activity = LocalActivity.current
  val context = LocalContext.current
  var menuOpen by remember { mutableStateOf(false) }
  var isSettingsSheetVisible by remember { mutableStateOf(false) }

  LaunchedEffect(state.shouldAutoStartStreaming, state.hasActiveDevice) {
    if (!state.shouldAutoStartStreaming || !state.hasActiveDevice) return@LaunchedEffect
    viewModel.consumeAutoStartStreamingRequest()
    viewModel.navigateToStreaming(onRequestWearablesPermission)
  }

  MaterialTheme(colorScheme = darkColorScheme()) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
      ConnectionMenu(
          modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding(),
          enabled = state.registrationState is RegistrationState.Registered,
          expanded = menuOpen,
          onExpandedChange = { menuOpen = it },
          onDisconnect = {
            activity?.let(viewModel::startUnregistration)
                ?: Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
          },
      )

      IconButton(
          onClick = { isSettingsSheetVisible = true },
          modifier = Modifier.align(Alignment.TopStart).systemBarsPadding(),
      ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.settings_button_title),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
      }

      NonStreamContent()

      StreamEntryArea(
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
          hasActiveDevice = state.hasActiveDevice,
          onStartStream = { viewModel.navigateToStreaming(onRequestWearablesPermission) },
      )

      if (state.isGettingStartedSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = viewModel::hideGettingStartedSheet,
            sheetState = sheetState,
        ) {
          GettingStartedSheetContent(
              onContinue = {
                scope.launch {
                  sheetState.hide()
                  viewModel.hideGettingStartedSheet()
                }
              }
          )
        }
      }

      if (isSettingsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsSheetVisible = false },
            sheetState = sheetState,
        ) {
          SettingsScreen(
              photoIntervalMs = state.photoIntervalMs,
              onPhotoIntervalChange = viewModel::setPhotoIntervalMs,
              serverHttpUrl = state.serverHttpUrl,
              onServerHttpUrlChange = viewModel::setServerHttpUrl,
          )
        }
      }
    }
  }
}

@Composable
private fun ConnectionMenu(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
  Box(modifier = modifier) {
    IconButton(onClick = { onExpandedChange(true) }) {
      Icon(
          imageVector = Icons.Default.LinkOff,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(28.dp),
      )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
      DropdownMenuItem(
          text = {
            Text(
                stringResource(R.string.unregister_button_title),
                color = if (enabled) AppColor.Red else Color.Gray,
            )
          },
          enabled = enabled,
          onClick = {
            onDisconnect()
            onExpandedChange(false)
          },
          modifier = Modifier.height(30.dp),
      )
    }
  }
}

@Composable
private fun NonStreamContent() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        painter = painterResource(R.drawable.camera_access_icon),
        contentDescription = stringResource(R.string.camera_access_icon_description),
        tint = Color.White,
        modifier = Modifier.size(80.dp * LocalDensity.current.density),
    )
    Text(
        text = stringResource(R.string.non_stream_screen_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = Color.White,
    )
    Text(
        text = stringResource(R.string.non_stream_screen_description),
        textAlign = TextAlign.Center,
        color = Color.White,
    )
  }
}

@Composable
private fun StreamEntryArea(
    modifier: Modifier = Modifier,
    hasActiveDevice: Boolean,
    onStartStream: () -> Unit,
) {
  Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (!hasActiveDevice) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(bottom = 12.dp),
      ) {
        Icon(
            painter = painterResource(R.drawable.hourglass_icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.waiting_for_active_device),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
      }
    }

    SwitchButton(
        label = stringResource(R.string.stream_button_title),
        onClick = onStartStream,
        enabled = hasActiveDevice,
    )
  }
}

@Composable
private fun GettingStartedSheetContent(onContinue: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
        text = stringResource(R.string.getting_started_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      listOf(
              R.drawable.camera_icon to R.string.getting_started_tip_permission,
              R.drawable.tap_icon to R.string.getting_started_tip_photo,
              R.drawable.smart_glasses_icon to R.string.getting_started_tip_led,
          )
          .forEach { (icon, text) -> TipItem(iconResId = icon, text = stringResource(text)) }
    }

    SwitchButton(
        label = stringResource(R.string.getting_started_continue),
        onClick = onContinue,
        modifier = Modifier.navigationBarsPadding(),
    )
  }
}

@Composable
private fun TipItem(iconResId: Int, text: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth()) {
    Icon(
        painter = painterResource(iconResId),
        contentDescription = null,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).width(24.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Text(text = text)
  }
}
