package com.slskdandroid.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text that a ViewModel can put in its UiState without holding a `Context`.
 *
 * Most UI text is a [Res] — a string resource id the Composable resolves. But slskd hands back
 * human-readable failures of its own (`SlskdConnectionTester` produces "Authentication failed —
 * check the API key", and every repository surfaces `Throwable.message`), and those can't be
 * resource ids. [Raw] carries that server/exception text through unchanged.
 *
 * Prefer [Res] whenever the string is ours: it's the only form that can be translated, and it keeps
 * ViewModel tests asserting on a stable id rather than on English prose.
 */
sealed interface UiText {

    /** A localized string resource, with optional format arguments. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    /** Text that already arrived as a string — a server message or an exception's message. */
    data class Raw(val value: String) : UiText
}

/** Resolves a [UiText] against the current configuration. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }
}

/**
 * The idiomatic way to build an error for a UiState: whatever the throwable said, else our own
 * localized fallback. Keeps `it.message ?: "hardcoded English"` out of every repository's `catch`.
 */
fun Throwable.toUiText(@StringRes fallback: Int, vararg args: Any): UiText =
    message?.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: UiText.Res(fallback, args.toList())
