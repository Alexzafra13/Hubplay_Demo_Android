package com.alex.hubplay.ui

import retrofit2.HttpException

// HTTP status codes we special-case for user-facing copy.
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_SERVER_ERR_MIN = 500
private const val HTTP_SERVER_ERR_MAX = 599

/**
 * Maps a [Throwable] from a failed API/network call to a clean, user-facing
 * Spanish message — **never** the raw exception text.
 *
 * The repos use `runCatching` and let exceptions surface to the ViewModels,
 * which historically did `error = err.message ?: "<fallback>"`. But
 * `Throwable.message` for an `HttpException`/`IOException` is technical and
 * often English ("Unable to resolve host", "HTTP 500 Internal Server Error")
 * — and `PlayerViewModel` even leaked internal endpoint paths. Rendering that
 * on a TV is poor UX and a Play-Store quality smell.
 *
 * [fallback] is the screen-specific copy shown when the error isn't a
 * recognised network/HTTP condition (e.g. "No se pudo cargar el detalle").
 *
 * Pure + JVM-testable (no Android, no Context). NOTE: this still hardcodes
 * Spanish — making it locale-aware via `strings.xml` needs a string resolver
 * injected into the VMs and is tracked as a separate refactor in
 * docs/memory. Centralising the copy here is the prerequisite for that.
 */
internal fun friendlyError(err: Throwable, fallback: String): String {
    val http = err as? HttpException ?: err.cause as? HttpException
    if (http != null) {
        return when (http.code()) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN ->
                "Tu sesión ha caducado. Vuelve a iniciar sesión."
            in HTTP_SERVER_ERR_MIN..HTTP_SERVER_ERR_MAX ->
                "El servidor tuvo un problema. Inténtalo de nuevo en un momento."
            else -> fallback
        }
    }
    // No HTTP response → a transport-level failure. Walk the cause chain
    // once (TLS/network errors are usually wrapped a couple of levels deep).
    var cur: Throwable? = err
    val seen = mutableSetOf<Throwable>()
    while (cur != null && seen.add(cur)) {
        val name = cur::class.java.simpleName
        when {
            name.contains("UnknownHost") ->
                return "No se pudo conectar. Comprueba tu conexión a internet."
            name.contains("SocketTimeout") ||
                name.contains("ConnectTimeout") ||
                name.contains("ConnectException") ->
                return "No se pudo conectar al servidor. Comprueba que esté encendido y accesible."
            name.contains("SSL") ||
                name.contains("CertPath") ||
                name.contains("Certificate") ->
                return "Error de certificado al contactar con el servidor."
        }
        cur = cur.cause
    }
    return fallback
}
