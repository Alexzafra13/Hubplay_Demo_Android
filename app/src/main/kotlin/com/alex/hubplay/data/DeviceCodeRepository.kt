package com.alex.hubplay.data

import com.alex.hubplay.api.AuthApi
import com.alex.hubplay.api.model.DeviceStartRequest
import com.alex.hubplay.api.model.DevicePollRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Owns the device-code pairing handshake.
 *
 * The flow:
 *   1. POST /auth/device/start → server returns a short user_code (e.g. "B4XK-9P2T")
 *      and a device_code (the long secret we'll poll with).
 *   2. UI shows user_code + instructions ("Open HubPlay in your browser
 *      → Settings → Devices → Enter this code").
 *   3. POST /auth/device/poll every 2s. Returns 202 (still waiting),
 *      403 (denied / expired), or 200 with access+refresh tokens.
 *   4. On 200 we persist tokens and serverUrl into TokenStore; the
 *      reactive AuthState flow flips and Compose navigates Home.
 *
 * The polling flow emits status updates so the UI can show
 * "Esperando aprobación…" / errors without owning the timer logic.
 */
class DeviceCodeRepository(
    private val authApi:    AuthApi,
    private val tokenStore: TokenStore,
) {

    suspend fun start(serverUrl: String): DeviceCodeStart {
        // Persist the server URL up-front so the BaseUrlInterceptor
        // reaches the right host. If pairing fails we'll wipe it again.
        tokenStore.storeServerUrl(serverUrl)
        val resp = authApi.deviceStart(DeviceStartRequest())
        return DeviceCodeStart(
            userCode    = resp.userCode    ?: error("device/start returned no user_code"),
            deviceCode  = resp.deviceCode  ?: error("device/start returned no device_code"),
            verifyUrl   = resp.verificationUri ?: serverUrl,
            intervalSec = (resp.interval ?: 2).coerceAtLeast(1),
            expiresInSec= resp.expiresIn ?: 600,
        )
    }

    /**
     * Cold flow that polls /auth/device/poll until the server returns
     * success, denial, or the code expires. Cancellable from the UI by
     * cancelling the collecting coroutine.
     */
    fun poll(start: DeviceCodeStart): Flow<DeviceCodeStatus> = flow {
        emit(DeviceCodeStatus.Pending)
        val deadline = System.currentTimeMillis() + start.expiresInSec * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(start.intervalSec * 1000L)
            val result = runCatching {
                authApi.devicePoll(DevicePollRequest(deviceCode = start.deviceCode))
            }
            val resp = result.getOrNull()
            if (resp == null) {
                // Network blip — keep polling, don't surface every transient error.
                continue
            }

            val access  = resp.accessToken
            val refresh = resp.refreshToken
            if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                tokenStore.storeTokens(access, refresh)
                emit(DeviceCodeStatus.Approved)
                return@flow
            }

            // Server still waiting — loop.
            emit(DeviceCodeStatus.Pending)
        }

        emit(DeviceCodeStatus.Expired)
    }
}

data class DeviceCodeStart(
    val userCode:     String,
    val deviceCode:   String,
    val verifyUrl:    String,
    val intervalSec:  Int,
    val expiresInSec: Int,
)

sealed interface DeviceCodeStatus {
    data object Pending  : DeviceCodeStatus
    data object Approved : DeviceCodeStatus
    data object Expired  : DeviceCodeStatus
    data class  Failed(val message: String) : DeviceCodeStatus
}
