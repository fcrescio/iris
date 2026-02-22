/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: SettingsScreen exposes stream interval tuning with stepped controls.

package li.crescio.penates.iris.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Date
import kotlin.math.roundToInt
import li.crescio.penates.iris.R
import li.crescio.penates.iris.stream.ConnectionDebugLogEntry

private const val MIN_INTERVAL_MS = 5000f
private const val MAX_INTERVAL_MS = 20000f
private const val INTERVAL_STEP_MS = 1000f

@Composable
fun SettingsScreen(
    photoIntervalMs: Long,
    onPhotoIntervalChange: (Long) -> Unit,
    serverHttpUrl: String,
    onServerHttpUrlChange: (String) -> Unit,
    ermetePsk: String,
    onErmetePskChange: (String) -> Unit,
    connectionDebugLog: List<ConnectionDebugLogEntry> = emptyList(),
    onClearConnectionDebugLog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current
  val logText = connectionDebugLog.toDebugText()
  val emptyDebugLogText = stringResource(R.string.settings_connection_debug_empty)

  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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

    OutlinedTextField(
        value = ermetePsk,
        onValueChange = onErmetePskChange,
        label = { Text(stringResource(R.string.settings_ermete_psk_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.settings_connection_debug_title),
        style = MaterialTheme.typography.titleMedium,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
          onClick = {
            clipboardManager.setText(AnnotatedString(logText.ifEmpty { emptyDebugLogText }))
          },
          modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(R.string.settings_connection_debug_copy))
      }

      OutlinedButton(onClick = onClearConnectionDebugLog, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.settings_connection_debug_clear))
      }
    }

    OutlinedTextField(
        value = logText.ifEmpty { emptyDebugLogText },
        onValueChange = {},
        readOnly = true,
        minLines = 8,
        maxLines = 12,
        label = { Text(stringResource(R.string.settings_connection_debug_log_label)) },
        modifier = Modifier.fillMaxWidth(),
    )

    SelectionContainer {
      Text(
          text = stringResource(R.string.settings_connection_debug_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun Float.toSteppedInterval(): Long {
  val step = ((this - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).roundToInt()
  return (MIN_INTERVAL_MS + (step * INTERVAL_STEP_MS)).toLong()
}

private fun List<ConnectionDebugLogEntry>.toDebugText(): String =
    joinToString(separator = "\n") { entry ->
      val timestamp = DateFormat.format("HH:mm:ss.SSS", Date(entry.timestampMs)).toString()
      "[$timestamp] ${entry.message}"
    }
