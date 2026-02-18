/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

package li.crescio.penates.iris.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SwitchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
  val colors =
      ButtonDefaults.buttonColors(
          containerColor = if (isDestructive) AppColor.DestructiveBackground else AppColor.DeepBlue,
          contentColor = if (isDestructive) AppColor.DestructiveForeground else Color.White,
          disabledContainerColor = Color.Gray,
          disabledContentColor = Color.DarkGray,
      )

  Button(
      modifier = modifier.height(56.dp).fillMaxWidth(),
      onClick = onClick,
      colors = colors,
      enabled = enabled,
  ) {
    Text(label)
  }
}
