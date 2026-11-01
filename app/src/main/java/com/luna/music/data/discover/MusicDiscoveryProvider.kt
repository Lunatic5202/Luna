package com.luna.music.data.discover

import android.net.Uri

/**
 * Metadata returned by a discovery source. Provider IDs are kept separate from MediaStore IDs:
 * a catalog result is not a local library Track until an audio file has actually been imported.
 */
data class CatalogTrack(
    val providerId: String,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUri: Uri?,
    val durationMs: Long?
)

/** A download option advertised by a provider for a track. Quality is descriptive, not inferred. */
data class DownloadVariant(
    val id: String,
    val label: String,
    val format: String,
    val qualityLabel: String?,
    val mimeType: String?
)

/**
 * A short-lived, direct HTTPS file location supplied for an authorized download.
 * Implementations must not return DRM-protected streams, subscription-only media, or URLs that
 * require bypassing access controls. The download layer must validate the scheme again.
 */
data class ResolvedAudioDownload(
    val uri: Uri,
    val fileName: String,
    val mimeType: String?
)

/**
 * Boundary between LUNA's native Discover UI and external catalog/download providers.
 *
 * This deliberately does not prescribe private website endpoints. A provider is only registered
 * after its integration method is documented or explicitly permitted by that provider.
 */
interface MusicDiscoveryProvider {
    val id: String
    val displayName: String

    suspend fun search(query: String): List<CatalogTrack>

    suspend fun availableDownloads(track: CatalogTrack): List<DownloadVariant>

    suspend fun resolveDownload(
        track: CatalogTrack,
        variant: DownloadVariant
    ): ResolvedAudioDownload
}
