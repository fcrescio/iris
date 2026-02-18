/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// StreamScreen - DAT Camera Streaming UI
//
// This composable demonstrates the main streaming UI for DAT camera functionality. It shows how to
// display live video from wearable devices and handle photo capture.

package li.crescio.penates.iris.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import li.crescio.penates.iris.R
import li.crescio.penates.iris.stream.StreamUiState
import li.crescio.penates.iris.stream.StreamViewModel
import li.crescio.penates.iris.wearables.WearablesViewModel

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
  val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()
  val wearablesUiState by wearablesViewModel.uiState.collectAsStateWithLifecycle()
  val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

  LaunchedEffect(Unit) {
    streamViewModel.startStream()
  }

  LaunchedEffect(wearablesUiState.photoIntervalMs) {
    streamViewModel.startAutoCapture(wearablesUiState.photoIntervalMs)
  }

  HorizontalPager(
      state = pagerState,
      modifier = modifier.fillMaxSize(),
  ) { page ->
    when (page) {
      0 ->
          StreamCameraPage(
              streamUiState = streamUiState,
              onStopStreaming = {
                streamViewModel.stopAutoCapture()
                streamViewModel.stopStream()
                wearablesViewModel.navigateToDeviceSelection()
              },
              onCapturePhoto = { streamViewModel.capturePhoto() },
          )
      1 ->
          SettingsScreen(
              photoIntervalMs = wearablesUiState.photoIntervalMs,
              onPhotoIntervalChange = wearablesViewModel::setPhotoIntervalMs,
          )
    }
  }
}

@Composable
private fun StreamCameraPage(
    streamUiState: StreamUiState,
    onStopStreaming: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    streamUiState.capturedPhoto?.let { capturedPhoto ->
      Image(
          bitmap = capturedPhoto.asImageBitmap(),
          contentDescription = stringResource(R.string.captured_photo),
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
      )
    }
    if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
      CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center),
      )
    }

    Box(modifier = Modifier.fillMaxSize().padding(all = 24.dp)) {
      Row(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .fillMaxWidth()
                  .height(56.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        SwitchButton(
            label = stringResource(R.string.stop_stream_button_title),
            onClick = onStopStreaming,
            isDestructive = true,
            modifier = Modifier.weight(1f),
        )

        // Photo capture button
        CaptureButton(
            onClick = onCapturePhoto,
        )
      }
    }
  }
}
