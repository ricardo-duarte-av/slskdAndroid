package com.slskdandroid.core.model

/**
 * How the nested result/transfer cards (Search, Downloads, Uploads) are tinted. Chosen in
 * Settings → Card style; both keep the same hue per depth and deepen with nesting.
 */
enum class CardTintStyle {
    /** Material 3 neutral surface tonal ladder (default). */
    Neutral,

    /** Primary-accent tint that strengthens with nesting depth. */
    Accent;

    companion object {
        val Default = Neutral
    }
}
