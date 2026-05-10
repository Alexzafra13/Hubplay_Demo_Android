package com.alex.hubplay.data

import com.alex.hubplay.data.api.AuthApi
import com.alex.hubplay.data.api.dto.DeviceStartRequest
import com.alex.hubplay.data.api.dto.DevicePollRequest
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
 *   3. POST /auth/device/poll every 2s. While pending the server returns
 *      400 with an ErrorEnvelope (Retrofit raises HttpException, which
 *      we catch and treat as "still waiting"); on approval it returns
 *      200 with the AuthToken envelope and we persist it.
 *   4. On 200 we persist tokens and serverUrl into TokenStore; the
 *      reactive AuthState flow flips and Compose navigates Home.
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
        val data = resp.data ?: error("device/start returned no data envelope")
        return DeviceCodeStart(
            userCode    = data.userCode    ?: error("device/start returned no user_code"),
            deviceCode  = data.deviceCode  ?: error("device/start returned no device_code"),
            verifyUrl   = data.verificationUrl ?: data.verificationUri ?: serverUrl,
            intervalSec = (data.interval ?: 2).coerceAtLeast(1),
            expiresInSec= data.expiresIn ?: 600,
        )
    }

    /**
     * Cold flow that polls /auth/device/poll until the server returns
     * success, denial, or the code expires. Cancellable from the UI by
     * cancelling the collecting coroutine.
     *
     * Pending state is signalled by the server with HTTP 4xx (RFC 8628
     * `authorization_pending` / `slow_down`), which Retrofit surfaces
     * as a thrown HttpException — we catch it and keep polling.
     */
    fun poll(start: DeviceCodeStart): Flow<DeviceCodeStatus> = flow {
        emit(DeviceCodeStatus.Pending)
        val deadline = System.currentTimeMillis() + start.expiresInSec * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(start.intervalSec * 1000L)
            val resp = runCatching {
                authApi.devicePoll(DevicePollRequest(deviceCode = start.deviceCode))
            }.getOrNull()

            // Failure means either: a transient network blip OR the
            // server returned 4xx (still pending / slow_down). Either
            // way the right move is to keep polling silently.
            if (resp == null) {
                emit(DeviceCodeStatus.Pending)
                continue
            }

            val token = resp.data
            val access  = token?.accessToken
            val refresh = token?.refreshToken
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
