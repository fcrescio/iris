/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: MockDeviceKitScreen provides compact controls for simulator-driven QA.

package li.crescio.penates.iris.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.crescio.penates.iris.R
import li.crescio.penates.iris.mockdevicekit.MockDeviceInfo
import li.crescio.penates.iris.mockdevicekit.MockDeviceKitViewModel

@Composable
fun MockDeviceKitScreen(
    modifier: Modifier = Modifier,
    viewModel: MockDeviceKitViewModel = viewModel(LocalActivity.current as ComponentActivity),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    HeaderCard(
        pairedCount = state.pairedDevices.size,
        onPair = viewModel::pairRaybanMeta,
        canPairMore = state.pairedDevices.size < 3,
    )

    state.pairedDevices.forEach { info -> MockDeviceCard(deviceInfo = info, viewModel = viewModel) }
  }
}

@Composable
private fun HeaderCard(pairedCount: Int, onPair: () -> Unit, canPairMore: Boolean) {
  SurfaceCard {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(R.string.mock_device_kit_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = stringResource(R.string.devices_paired_count, pairedCount),
          style = MaterialTheme.typography.bodyMedium,
          color = AppColor.Green,
      )
    }

    Text(
        text = stringResource(R.string.mock_device_kit_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider()

    ActionButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(R.string.pair_rayban_meta),
        onClick = onPair,
        enabled = canPairMore,
    )
  }
}

@Composable
private fun MockDeviceCard(deviceInfo: MockDeviceInfo, viewModel: MockDeviceKitViewModel) {
  val videoPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setCameraFeed(deviceInfo, it) }
      }
  val imagePicker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setCapturedImage(deviceInfo, it) }
      }

  SurfaceCard {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(deviceInfo.deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            deviceInfo.deviceId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      ActionButton(
          text = stringResource(R.string.unpair),
          onClick = { viewModel.unpairDevice(deviceInfo) },
          containerColor = AppColor.Red,
      )
    }

    HorizontalDivider()
    ControlRow(
        stringResource(R.string.power_on) to { viewModel.powerOn(deviceInfo) },
        stringResource(R.string.power_off) to { viewModel.powerOff(deviceInfo) },
    )
    ControlRow(
        stringResource(R.string.unfold) to { viewModel.unfold(deviceInfo) },
        stringResource(R.string.fold) to { viewModel.fold(deviceInfo) },
    )
    ControlRow(
        stringResource(R.string.don) to { viewModel.don(deviceInfo) },
        stringResource(R.string.doff) to { viewModel.doff(deviceInfo) },
    )

    MediaRow(
        actionLabel = stringResource(R.string.select_video),
        onPick = { videoPicker.launch("video/*") },
        status =
            if (deviceInfo.hasCameraFeed) stringResource(R.string.has_camera_feed)
            else stringResource(R.string.no_camera_feed),
        statusPositive = deviceInfo.hasCameraFeed,
    )
    MediaRow(
        actionLabel = stringResource(R.string.select_image),
        onPick = { imagePicker.launch("image/*") },
        status =
            if (deviceInfo.hasCapturedImage) stringResource(R.string.has_captured_image)
            else stringResource(R.string.no_captured_image),
        statusPositive = deviceInfo.hasCapturedImage,
    )
  }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
  }
}

@Composable
private fun ControlRow(
    first: Pair<String, () -> Unit>,
    second: Pair<String, () -> Unit>,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ActionButton(text = first.first, onClick = first.second, modifier = Modifier.weight(1f))
    ActionButton(text = second.first, onClick = second.second, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun MediaRow(actionLabel: String, onPick: () -> Unit, status: String, statusPositive: Boolean) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ActionButton(text = actionLabel, onClick = onPick, modifier = Modifier.weight(1f))
    Text(
        text = status,
        color = if (statusPositive) AppColor.Green else AppColor.Yellow,
        textAlign = TextAlign.Left,
        modifier = Modifier.weight(1f).padding(start = 8.dp),
    )
  }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppColor.DeepBlue,
    contentColor: Color = Color.White,
) {
  Button(
      modifier = modifier,
      colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
      onClick = onClick,
      enabled = enabled,
  ) {
    Text(text, fontWeight = FontWeight.Medium)
  }
}
