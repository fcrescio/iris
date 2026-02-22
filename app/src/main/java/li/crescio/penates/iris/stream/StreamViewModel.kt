/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: StreamViewModel manages session lifecycle, auto capture, and image decoding.

package li.crescio.penates.iris.stream

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.crescio.penates.iris.wearables.WearablesViewModel

class StreamViewModel(
    application: Application,
    private val wearablesViewModel: WearablesViewModel,
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "StreamViewModel"
    private val INITIAL = StreamUiState()
  }

  private val deviceSelector: DeviceSelector = wearablesViewModel.deviceSelector
  private val _uiState = MutableStateFlow(INITIAL)

  val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

  private var streamSession: StreamSession? = null
  private var streamStateJob: Job? = null
  private var autoCaptureJob: Job? = null
  private var connectionManager: ErmeteConnectionManager? = null
  private var lastCaptureBlockReason: String? = null

  fun startStream(serverHttpUrl: String, ermetePsk: String) {
    stopStream()
    appendConnectionDebugLog(
        ConnectionDebugLogEntry(
            timestampMs = System.currentTimeMillis(),
            message = "Starting DAT stream session and signaling connection",
        ))
    connectionManager =
        ErmeteConnectionManager(
                context = getApplication(),
                scope = viewModelScope,
                onConnectionStateChanged = { connectionState ->
                  _uiState.update { current -> current.copy(serverConnectionState = connectionState) }
                },
                onDebugLog = ::appendConnectionDebugLog,
            )
            .also { it.connect(serverHttpUrl, ermetePsk) }

    val newSession =
        Wearables.startStreamSession(
            getApplication(),
            deviceSelector,
            StreamConfiguration(videoQuality = VideoQuality.LOW),
        )

    streamSession = newSession
    streamStateJob =
        viewModelScope.launch {
          newSession.state.collect { state ->
            val previous = _uiState.value.streamSessionState
            _uiState.update { it.copy(streamSessionState = state) }
            if (previous != state) {
              appendConnectionDebugLog(
                  ConnectionDebugLogEntry(
                      timestampMs = System.currentTimeMillis(),
                      message = "Wearable stream session state: $previous -> $state",
                  ))
            }
            if (previous != state && state == StreamSessionState.STOPPED) {
              appendConnectionDebugLog(
                  ConnectionDebugLogEntry(
                      timestampMs = System.currentTimeMillis(),
                      message = "Wearable stream stopped; disconnecting",
                  ))
              stopStream()
              wearablesViewModel.navigateToDeviceSelection()
            }
          }
        }
  }

  fun stopStream() {
    appendConnectionDebugLog(
        ConnectionDebugLogEntry(
            timestampMs = System.currentTimeMillis(),
            message = "Stopping stream session",
        ))
    stopAutoCapture()
    streamStateJob?.cancel()
    streamStateJob = null
    streamSession?.close()
    connectionManager?.close()
    connectionManager = null
    streamSession = null
    lastCaptureBlockReason = null
    _uiState.update { INITIAL }
  }

  fun startAutoCapture(intervalMs: Long = 10_000L) {
    stopAutoCapture()
    autoCaptureJob =
        viewModelScope.launch {
          while (isActive) {
            val state = wearablesViewModel.uiState.value
            capturePhoto(state.serverHttpUrl, state.ermetePsk)
            delay(intervalMs)
          }
        }
  }

  fun stopAutoCapture() {
    autoCaptureJob?.cancel()
    autoCaptureJob = null
  }


  fun clearConnectionDebugLog() {
    _uiState.update { current -> current.copy(connectionDebugLog = emptyList()) }
  }

  private fun appendConnectionDebugLog(entry: ConnectionDebugLogEntry) {
    _uiState.update { current ->
      current.copy(connectionDebugLog = (current.connectionDebugLog + entry).takeLast(250))
    }
  }

  fun capturePhoto(serverHttpUrl: String, ermetePsk: String) {
    val state = _uiState.value
    val blockReason =
        when {
          state.isCapturing -> "capture already in progress"
          state.streamSessionState != StreamSessionState.STREAMING ->
              "wearable session is ${state.streamSessionState}"
          state.serverConnectionState != ServerConnectionState.CONNECTED ->
              "server signaling is ${state.serverConnectionState}"
          else -> null
        }
    if (blockReason != null) {
      if (blockReason != lastCaptureBlockReason) {
        appendConnectionDebugLog(
            ConnectionDebugLogEntry(
                timestampMs = System.currentTimeMillis(),
                message = "Skipping snapshot: $blockReason",
            ))
        lastCaptureBlockReason = blockReason
      }
      return
    }

    lastCaptureBlockReason = null

    _uiState.update { it.copy(isCapturing = true) }
    viewModelScope.launch {
      try {
        appendConnectionDebugLog(
            ConnectionDebugLogEntry(
                timestampMs = System.currentTimeMillis(),
                message = "Triggering wearable snapshot",
            ))
        streamSession
            ?.capturePhoto()
            ?.onSuccess {
              runCatching {
                    val bitmap = decodePhoto(it)
                    _uiState.update { current -> current.copy(capturedPhoto = bitmap) }
                    appendConnectionDebugLog(
                        ConnectionDebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            message = "Snapshot captured (${bitmap.width}x${bitmap.height}), uploading",
                        ))
                    connectionManager?.sendFrame(serverHttpUrl, ermetePsk, bitmap.toJpegBytes())
                  }
                  .onFailure { error ->
                    appendConnectionDebugLog(
                        ConnectionDebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            message =
                                "Snapshot processing failed: ${error.message ?: "unknown error"}",
                        ))
                    Log.e(TAG, "Failed to process captured photo", error)
                  }
            }?.onFailure { error ->
              appendConnectionDebugLog(
                  ConnectionDebugLogEntry(
                      timestampMs = System.currentTimeMillis(),
                      message = "Wearable snapshot failed: ${error.message ?: "unknown error"}",
                  ))
              Log.e(TAG, "Photo capture failed", error)
            }
      } finally {
        _uiState.update { it.copy(isCapturing = false) }
      }
    }
  }

  private fun decodePhoto(photoData: PhotoData): Bitmap =
      when (photoData) {
        is PhotoData.Bitmap -> photoData.bitmap
        is PhotoData.HEIC -> decodeHeic(photoData)
      }

  private fun decodeHeic(photoData: PhotoData.HEIC): Bitmap {
    val bytes = ByteArray(photoData.data.remaining()).also(photoData.data::get)
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val transform = readExifTransform(bytes)
    return applyTransform(source, transform)
  }

  private fun readExifTransform(bytes: ByteArray): Matrix {
    val orientation =
        try {
          ByteArrayInputStream(bytes).use { input ->
            ExifInterface(input)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
          }
        } catch (error: IOException) {
          Log.w(TAG, "Failed to read EXIF from HEIC", error)
          ExifInterface.ORIENTATION_NORMAL
        }

    return Matrix().apply {
      when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
          postRotate(90f)
          postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
          postRotate(270f)
          postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
      }
    }
  }

  private fun applyTransform(bitmap: Bitmap, transform: Matrix): Bitmap {
    if (transform.isIdentity) return bitmap

    return try {
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also {
        if (it != bitmap) bitmap.recycle()
      }
    } catch (oom: OutOfMemoryError) {
      Log.e(TAG, "Failed to rotate/mirror image", oom)
      bitmap
    }
  }

  override fun onCleared() {
    stopStream()
    super.onCleared()
  }

  class Factory(
      private val application: Application,
      private val wearablesViewModel: WearablesViewModel,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(StreamViewModel::class.java)) {
        @Suppress("UNCHECKED_CAST", "KotlinGenericsCast")
        return StreamViewModel(application, wearablesViewModel) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}

private fun Bitmap.toJpegBytes(): ByteArray {
  val output = ByteArrayOutputStream()
  compress(Bitmap.CompressFormat.JPEG, 90, output)
  return output.toByteArray()
}
