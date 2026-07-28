package com.slskdandroid.core.model

import java.io.IOException

/**
 * Why a connection attempt against a slskd instance failed.
 *
 * Exists so the *reason* travels to the UI instead of an English sentence. The connection tester
 * previously threw `IOException("Authentication failed — check the API key")`, which reached the
 * screen verbatim and could never be translated. The UI now maps these cases to string resources.
 *
 * Lives in `core:model` rather than `core:network` so feature modules can pattern-match on it
 * without depending on the networking layer (they already see `core:model` through `core:data`).
 */
sealed interface ConnectionFailure {

    /** The base URL isn't a usable HTTP(S) URL. */
    data object InvalidUrl : ConnectionFailure

    /** Reached slskd, but it rejected the API key (401/403). */
    data object AuthRejected : ConnectionFailure

    /** Reached slskd, but it answered with an unexpected status. */
    data class HttpError(val code: Int) : ConnectionFailure

    /** Never got an answer — wrong host/port, no route, TLS failure, timeout. */
    data object Unreachable : ConnectionFailure
}

/**
 * Carries a [ConnectionFailure] through the `Result<Unit>` the tester and repository already use.
 *
 * Deliberately has no `message`: anything non-null would be untranslated English that
 * `Throwable.toUiText` would prefer over the localized fallback.
 */
class ConnectionFailureException(val failure: ConnectionFailure) : IOException()
