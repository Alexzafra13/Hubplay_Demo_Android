package com.alex.hubplay.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.cert.X509Certificate

/**
 * A pending "this server's cert isn't in the OS trust store — do you
 * want to pin it?" decision. Published by [PinnedCertTrustManager] when
 * an unknown cert is seen on a connect; consumed by the global UI
 * (Login screen today; the dialog could move to the app root later if
 * cert rotation while paired becomes a common case).
 *
 * @Immutable: the [cert] field is technically a non-stable reference but
 * the data class as a whole is value-typed by fingerprint+host (see
 * equals/hashCode below) so Compose treats it as stable.
 */
@Immutable
data class CertChallenge(
    val host:        String,
    val fingerprint: String,
    val subject:     String,
    val issuer:      String,
    val notBefore:   Long,
    val notAfter:    Long,
    val reason:      CertFailureReason,
    /** Leaf cert — used to pin on accept. Excluded from equals/hashCode. */
    val cert:        X509Certificate,
) {
    override fun equals(other: Any?): Boolean =
        other is CertChallenge && other.host == host && other.fingerprint == fingerprint

    override fun hashCode(): Int = host.hashCode() * 31 + fingerprint.hashCode()
}

enum class CertFailureReason {
    /** CA not in the OS trust store (e.g. ISRG Root X2 on Android <14). */
    UnknownIssuer,

    /** Cert is past notAfter. */
    Expired,

    /** Cert's notBefore is in the future — usually a wrong device clock. */
    NotYetValid,

    /** SAN / CN doesn't include the URL the user typed. */
    HostnameMismatch,

    /** Anything else the JDK couldn't validate. */
    Other,
}

/**
 * Single-channel bus between [PinnedCertTrustManager] (publisher) and
 * the Login UI (consumer). Holds at most one pending challenge — a new
 * one replaces the previous (the user is on a new attempt, the second
 * cert is the relevant one). On accept we pin via [CertPinStore] and
 * emit on [accepted] so the caller can auto-retry the original request.
 *
 * Pinning runs on [scope] because put() is suspend (file write) and the
 * accept call site is non-suspending (Compose button click handler).
 */
class CertChallengeBus(
    private val pinStore: CertPinStore,
    private val scope:    CoroutineScope,
) {
    private val _pending  = MutableStateFlow<CertChallenge?>(null)
    val pending: StateFlow<CertChallenge?> = _pending.asStateFlow()

    private val _accepted = MutableSharedFlow<CertChallenge>(extraBufferCapacity = 4)
    val accepted: SharedFlow<CertChallenge> = _accepted.asSharedFlow()

    fun publish(challenge: CertChallenge) {
        _pending.value = challenge
    }

    fun accept(challenge: CertChallenge) {
        // Snapshot the field — if a second challenge raced in we'd
        // accept the older one inadvertently. The pin is host+leaf so
        // a stale acceptance for the wrong cert can't grant access.
        scope.launch {
            pinStore.put(challenge.host, challenge.cert)
            _accepted.emit(challenge)
            _pending.compareAndSet(challenge, null)
        }
    }

    fun dismiss() {
        _pending.value = null
    }
}
