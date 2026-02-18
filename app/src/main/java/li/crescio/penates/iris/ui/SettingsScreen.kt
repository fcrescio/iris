package li.crescio.penates.iris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import li.crescio.penates.iris.R
import kotlin.math.roundToInt

private const val MIN_INTERVAL_MS = 500f
private const val MAX_INTERVAL_MS = 5000f
private const val INTERVAL_STEP_MS = 500f

@Composable
fun SettingsScreen(
    photoIntervalMs: Long,
    onPhotoIntervalChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineSmall,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
          text =
              stringResource(
                  R.string.settings_interval_label,
                  photoIntervalMs,
                  stringResource(R.string.settings_interval_unit),
              ),
          style = MaterialTheme.typography.bodyLarge,
      )

      Slider(
          value = photoIntervalMs.toFloat().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS),
          onValueChange = { value ->
            val steps = ((value - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).roundToInt()
            val steppedValue = MIN_INTERVAL_MS + steps * INTERVAL_STEP_MS
            onPhotoIntervalChange(steppedValue.toLong())
          },
          valueRange = MIN_INTERVAL_MS..MAX_INTERVAL_MS,
          steps = ((MAX_INTERVAL_MS - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).toInt() - 1,
      )
    }
  }
}
