/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: StreamScreen renders stream controls and settings in a compact pager layout.

package li.crescio.penates.iris.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import li.crescio.penates.iris.R
import li.crescio.penates.iris.stream.ServerConnectionState
import li.crescio.penates.iris.stream.StreamUiState
import li.crescio.penates.iris.stream.StreamViewModel
import li.crescio.penates.iris.wearables.WearablesViewModel

private const val STREAM_PAGE = 0
private const val SETTINGS_PAGE = 1

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        ),
) {
  val streamState by streamViewModel.uiState.collectAsStateWithLifecycle()
  val wearablesState by wearablesViewModel.uiState.collectAsStateWithLifecycle()
  val pagerState = rememberPagerState(initialPage = STREAM_PAGE, pageCount = { 2 })

  LaunchedEffect(wearablesState.serverHttpUrl, wearablesState.ermetePsk) {
    streamViewModel.startStream(wearablesState.serverHttpUrl, wearablesState.ermetePsk)
  }
  LaunchedEffect(wearablesState.photoIntervalMs) {
    streamViewModel.startAutoCapture(wearablesState.photoIntervalMs)
  }

  HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { page ->
    when (page) {
      STREAM_PAGE ->
          StreamCameraPage(
              uiState = streamState,
              onStopStreaming = {
                streamViewModel.stopAutoCapture()
                streamViewModel.stopStream()
                wearablesViewModel.navigateToDeviceSelection()
              },
              onCapturePhoto = {
                streamViewModel.capturePhoto(wearablesState.serverHttpUrl, wearablesState.ermetePsk)
              },
          )
      SETTINGS_PAGE ->
          SettingsScreen(
              photoIntervalMs = wearablesState.photoIntervalMs,
              onPhotoIntervalChange = wearablesViewModel::setPhotoIntervalMs,
              serverHttpUrl = wearablesState.serverHttpUrl,
              onServerHttpUrlChange = wearablesViewModel::setServerHttpUrl,
              ermetePsk = wearablesState.ermetePsk,
              onErmetePskChange = wearablesViewModel::setErmetePsk,
              connectionDebugLog = streamState.connectionDebugLog,
              onClearConnectionDebugLog = streamViewModel::clearConnectionDebugLog,
          )
    }
  }
}

@Composable
private fun StreamCameraPage(
    uiState: StreamUiState,
    onStopStreaming: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    WebRtcStatusIndicator(
        streamSessionState = uiState.streamSessionState,
        serverConnectionState = uiState.serverConnectionState,
        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
    )

    uiState.capturedPhoto?.let {
      Image(
          bitmap = it.asImageBitmap(),
          contentDescription = stringResource(R.string.captured_photo),
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
      )
    }

    if (uiState.streamSessionState == StreamSessionState.STARTING) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }

    Row(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      SwitchButton(
          label = stringResource(R.string.stop_stream_button_title),
          onClick = onStopStreaming,
          isDestructive = true,
          modifier = Modifier.weight(1f),
      )
      CaptureButton(onClick = onCapturePhoto)
    }
  }
}

@Composable
private fun WebRtcStatusIndicator(
    streamSessionState: StreamSessionState,
    serverConnectionState: ServerConnectionState,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier.background(
              color = Color.Black.copy(alpha = 0.7f),
              shape = RoundedCornerShape(12.dp),
          ).padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Text(
        text =
            stringResource(
                R.string.webrtc_status_label,
                streamSessionState.name.lowercase().replaceFirstChar(Char::uppercase),
                serverConnectionState.name.lowercase().replaceFirstChar(Char::uppercase),
            ),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
    )
  }
}
