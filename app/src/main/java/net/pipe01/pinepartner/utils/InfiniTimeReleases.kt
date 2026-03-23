package net.pipe01.pinepartner.utils

import android.net.Uri
import fuel.Fuel
import fuel.get
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.core.net.toUri

data class InfiniTimeRelease(
    val version: String,
    val name: String,
    val resourcesUri: Uri,
    val firmwareUri: Uri,
    val firmwareSizeBytes: Long,
)

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val assets: List<GitHubAsset>,
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val size: Long,
    val browser_download_url: String,
)

private val json = Json { ignoreUnknownKeys = true }

// This endpoint caches the response of https://api.github.com/repos/InfiniTimeOrg/InfiniTime/releases
private const val endpoint = "https://pipe01.net/pinepartner-proxy/releases"

suspend fun getInfiniTimeReleases(): List<InfiniTimeRelease> {
    val resp = Fuel.get(endpoint).source.readString()

    val ghReleases = json.decodeFromString<List<GitHubRelease>>(resp)

    return ghReleases.mapNotNull {
        val resources = it.assets.firstOrNull { it.name.startsWith("infinitime-resources-") } ?: return@mapNotNull null
        val firmware = it.assets.firstOrNull { it.name.startsWith("pinetime-mcuboot-app-dfu-") } ?: return@mapNotNull null

        InfiniTimeRelease(
            version = it.tag_name,
            name = it.name.trim(),
            resourcesUri = resources.browser_download_url.toUri(),
            firmwareUri = firmware.browser_download_url.toUri(),
            firmwareSizeBytes = firmware.size,
        )
    }
}
