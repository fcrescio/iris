/*
 * Copyright (c) 2026 Crescio.
 *
 * This file is part of Iris and is distributed under the
 * terms described in the LICENSE file at the repository root.
 */

// Iris: WearablesUiState is the single source of truth for app-level wearable status.

package li.crescio.penates.iris.wearables

import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import li.crescio.penates.iris.BuildConfig

data class WearablesUiState(
    val registrationState: RegistrationState = RegistrationState.Unavailable(),
    val devices: ImmutableList<DeviceIdentifier> = persistentListOf(),
    val recentError: String? = null,
    val hasActiveDevice: Boolean = false,
    val hasMockDevices: Boolean = false,
    val isStreaming: Boolean = false,
    val isDebugMenuVisible: Boolean = false,
    val isGettingStartedSheetVisible: Boolean = false,
    val shouldAutoStartStreaming: Boolean = false,
    val photoIntervalMs: Long = 10000L,
    val serverHttpUrl: String = BuildConfig.DEFAULT_WEBRTC_ADDRESS,
    val ermetePsk: String = BuildConfig.DEFAULT_ERMETE_PSK,
) {
  val isRegistered: Boolean
    get() = registrationState is RegistrationState.Registered || hasMockDevices
}
