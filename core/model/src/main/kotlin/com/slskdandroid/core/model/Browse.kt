package com.slskdandroid.core.model

/**
 * One directory from a peer's browse result. [directory] is the full remote path; [files] carry
 * absolute filenames (the base names returned by slskd are joined to the directory) so they can be
 * downloaded directly. Reuses [SearchResultFile] as the generic remote-file descriptor.
 */
data class BrowseDirectory(
    val directory: String,
    val files: List<SearchResultFile>,
)
