/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: HomeScreen introduces wearable registration with concise guidance cards.

package li.crescio.penates.iris.ui

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.crescio.penates.iris.R
import li.crescio.penates.iris.wearables.WearablesViewModel

@Composable
fun HomeScreen(viewModel: WearablesViewModel, modifier: Modifier = Modifier) {
  val activity = LocalActivity.current
  val context = LocalContext.current

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(24.dp)
              .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Spacer(modifier = Modifier.weight(1f))
    HomeHero()
    Spacer(modifier = Modifier.weight(1f))

    Text(
        text = stringResource(R.string.home_redirect_message),
        color = Color.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )

    SwitchButton(
        label = stringResource(R.string.register_button_title),
        onClick = {
          activity?.let(viewModel::startRegistration)
              ?: Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
        },
    )
  }
}

@Composable
private fun HomeHero() {
  val tips =
      listOf(
          Triple(
              R.drawable.smart_glasses_icon,
              R.string.home_tip_video_title,
              R.string.home_tip_video,
          ),
          Triple(R.drawable.sound_icon, R.string.home_tip_audio_title, R.string.home_tip_audio),
          Triple(R.drawable.walking_icon, R.string.home_tip_hands_title, R.string.home_tip_hands),
      )

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        painter = painterResource(R.drawable.camera_access_icon),
        contentDescription = stringResource(R.string.camera_access_icon_description),
        tint = AppColor.DeepBlue,
        modifier = Modifier.size(80.dp * LocalDensity.current.density),
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      tips.forEach { (icon, title, body) ->
        TipItem(iconResId = icon, title = stringResource(title), text = stringResource(body))
      }
    }
  }
}

@Composable
private fun TipItem(iconResId: Int, title: String, text: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth()) {
    Icon(
        painter = painterResource(iconResId),
        contentDescription = null,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).width(24.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
      Text(text = text, color = Color.Gray)
    }
  }
}
