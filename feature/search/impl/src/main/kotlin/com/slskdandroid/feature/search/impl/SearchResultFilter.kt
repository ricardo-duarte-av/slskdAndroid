package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.model.SearchResponse
import com.slskdandroid.core.model.SearchResultFile

/** Result sort orders, matching the slskd web UI. */
enum class ResultSort(val label: String) {
    UploadSpeed("Upload Speed (Fastest to Slowest)"),
    QueueDepth("Queue Depth (Least to Most)"),
}

/**
 * Parsed result filter, mirroring slskd's web UI `parseFiltersFromString`. The filter box accepts
 * free text terms (a leading `-` excludes), `is{cbr,vbr,lossless,lossy}` flags, and
 * `min{bitrate,bitdepth,filesize,filesinfolder,length}:N` constraints.
 */
data class SearchFilters(
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val isCBR: Boolean = false,
    val isVBR: Boolean = false,
    val isLossless: Boolean = false,
    val isLossy: Boolean = false,
    val minBitRate: Int = 0,
    val minBitDepth: Int = 0,
    val minFileSize: Long = 0,
    val minLength: Int = 0,
    val minFilesInFolder: Int = 0,
)

/** The exact placeholder slskd shows in its filter box; doubles as syntax documentation. */
const val FILTER_PLACEHOLDER =
    "lackluster container -bothersome iscbr|isvbr islossless|islossy " +
        "minbitrate:320 minbitdepth:24 minfilesize:10 minfilesinfolder:8 minlength:5000"

private fun firstInt(text: String, regex: Regex): Int? =
    regex.find(text)?.groupValues?.get(2)?.toIntOrNull()

fun parseFilters(text: String): SearchFilters {
    fun re(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val minBitRate = firstInt(text, re("(minbr|minbitrate):(\\d+)")) ?: 0
    val minBitDepth = firstInt(text, re("(minbd|minbitdepth):(\\d+)")) ?: 0
    val minFileSize = firstInt(text, re("(minfs|minfilesize):(\\d+)"))?.toLong() ?: 0L
    val minLength = firstInt(text, re("(minlen|minlength):(\\d+)")) ?: 0
    val minFilesInFolder = firstInt(text, re("(minfif|minfilesinfolder):(\\d+)")) ?: 0

    val flags = setOf("isvbr", "iscbr", "islossless", "islossy")
    val terms = text.lowercase()
        .split(' ')
        .filter { it.isNotBlank() && !it.contains(':') && it !in flags }

    return SearchFilters(
        include = terms.filterNot { it.startsWith('-') },
        exclude = terms.filter { it.startsWith('-') }.map { it.removePrefix("-") },
        isCBR = re("iscbr").containsMatchIn(text),
        isVBR = re("isvbr").containsMatchIn(text),
        isLossless = re("islossless").containsMatchIn(text),
        isLossy = re("islossy").containsMatchIn(text),
        minBitRate = minBitRate,
        minBitDepth = minBitDepth,
        minFileSize = minFileSize,
        minLength = minLength,
        minFilesInFolder = minFilesInFolder,
    )
}

private fun SearchResultFile.matches(f: SearchFilters): Boolean {
    val vbr = isVariableBitRate
    if (f.isCBR && (vbr == null || vbr)) return false
    if (f.isVBR && (vbr == null || !vbr)) return false
    if (f.isLossless && ((sampleRate ?: 0) == 0 || (bitDepth ?: 0) == 0)) return false
    if (f.isLossy && ((sampleRate ?: 0) != 0 || (bitDepth ?: 0) != 0)) return false
    if ((bitRate ?: Int.MAX_VALUE) < f.minBitRate) return false
    if ((bitDepth ?: Int.MAX_VALUE) < f.minBitDepth) return false
    if (sizeBytes < f.minFileSize) return false
    if ((lengthSeconds ?: Int.MAX_VALUE) < f.minLength) return false

    val name = filename.lowercase()
    if (f.include.isNotEmpty() && !f.include.all { name.contains(it) }) return false
    if (f.exclude.any { name.contains(it) }) return false
    return true
}

/**
 * Applies [filters] to one response's files, returning a copy with only the matching files (and
 * locked files). Mirrors the web UI's `filterResponse`: a folder below `minFilesInFolder` is
 * dropped wholesale.
 */
fun SearchResponse.applyFilters(filters: SearchFilters): SearchResponse {
    if (files.size + lockedFiles.size < filters.minFilesInFolder) {
        return copy(files = emptyList(), lockedFiles = emptyList())
    }
    return copy(
        files = files.filter { it.matches(filters) },
        lockedFiles = lockedFiles.filter { it.matches(filters) },
    )
}

/** Comparator for the chosen [sort]: fastest-first for speed, shallowest-first for queue depth. */
fun comparatorFor(sort: ResultSort): Comparator<SearchResponse> = when (sort) {
    ResultSort.UploadSpeed -> compareByDescending { it.uploadSpeed }
    ResultSort.QueueDepth -> compareBy { it.queueLength }
}
