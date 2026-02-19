/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: SettingsScreen exposes stream interval tuning with stepped controls.

package li.crescio.penates.iris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import li.crescio.penates.iris.R

private const val MIN_INTERVAL_MS = 5000f
private const val MAX_INTERVAL_MS = 20000f
private const val INTERVAL_STEP_MS = 1000f

@Composable
fun SettingsScreen(
    photoIntervalMs: Long,
    onPhotoIntervalChange: (Long) -> Unit,
    serverHttpUrl: String,
    onServerHttpUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        text =
            stringResource(
                R.string.settings_interval_label,
                photoIntervalMs / 1000,
                stringResource(R.string.settings_interval_unit),
            ),
        style = MaterialTheme.typography.bodyLarge,
    )

    Slider(
        value = photoIntervalMs.toFloat().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS),
        onValueChange = { value -> onPhotoIntervalChange(value.toSteppedInterval()) },
        valueRange = MIN_INTERVAL_MS..MAX_INTERVAL_MS,
        steps = ((MAX_INTERVAL_MS - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).toInt() - 1,
    )

    OutlinedTextField(
        value = serverHttpUrl,
        onValueChange = onServerHttpUrlChange,
        label = { Text(stringResource(R.string.settings_server_http_url_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

private fun Float.toSteppedInterval(): Long {
  val step = ((this - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).roundToInt()
  return (MIN_INTERVAL_MS + (step * INTERVAL_STEP_MS)).toLong()
}
