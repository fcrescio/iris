/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

package li.crescio.penates.iris.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import li.crescio.penates.iris.R

@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
  Button(
      modifier = modifier.aspectRatio(1f),
      onClick = onClick,
      colors = ButtonDefaults.buttonColors(containerColor = Color.White),
      shape = CircleShape,
      contentPadding = PaddingValues(0.dp),
      content = content,
  )
}

@Composable
fun CaptureButton(onClick: () -> Unit) {
  CircleButton(onClick = onClick) {
    Icon(
        imageVector = Icons.Default.PhotoCamera,
        contentDescription = stringResource(R.string.capture_photo),
        tint = Color.Black,
    )
  }
}
