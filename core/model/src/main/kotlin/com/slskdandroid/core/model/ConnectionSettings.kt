package com.slskdandroid.core.model

/**
 * The user's slskd connection details. The app authenticates with an API key (no
 * username/password login), so both fields are required before any networking can occur.
 *
 * @property baseUrl the slskd web/API base URL, e.g. `http://192.168.1.10:5030`.
 * @property apiKey an slskd API key sent in the `X-API-Key` header.
 */
data class ConnectionSettings(
    val baseUrl: String,
    val apiKey: String,
)
