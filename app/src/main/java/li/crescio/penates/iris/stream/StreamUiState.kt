/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: StreamUiState tracks the currently rendered frame and capture status.

package li.crescio.penates.iris.stream

import android.graphics.Bitmap
import com.meta.wearable.dat.camera.types.StreamSessionState

data class StreamUiState(
    val streamSessionState: StreamSessionState = StreamSessionState.STOPPED,
    val serverConnectionState: ServerConnectionState = ServerConnectionState.DISCONNECTED,
    val capturedPhoto: Bitmap? = null,
    val isCapturing: Boolean = false,
)
