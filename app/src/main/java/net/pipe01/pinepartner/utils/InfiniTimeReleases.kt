package net.pipe01.pinepartner.utils

import android.net.Uri
import fuel.Fuel
import fuel.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class InfiniTimeRelease(
    val version: String,
    val name: String,
    val resourcesUri: Uri,
    val firmwareUri: Uri,
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
    val browser_download_url: String,
)

private val json = Json { ignoreUnknownKeys = true }

// This endpoint caches the response of https://api.github.com/repos/InfiniTimeOrg/InfiniTime/releases
private const val endpoint = "https://pipe01.net/pinepartner-proxy/releases"

suspend fun getInfiniTimeReleases(): List<InfiniTimeRelease> {
    val resp = Fuel.get(endpoint).body

    val ghReleases = json.decodeFromString<List<GitHubRelease>>(resp)

    return ghReleases.mapNotNull {
        InfiniTimeRelease(
            version = it.tag_name,
            name = it.name.trim(),
            resourcesUri = Uri.parse(it.assets.firstOrNull { it.name.startsWith("infinitime-resources-") }?.browser_download_url ?: return@mapNotNull null),
            firmwareUri = Uri.parse(it.assets.firstOrNull { it.name.startsWith("pinetime-mcuboot-app-dfu-") }?.browser_download_url ?: return@mapNotNull null),
        )
    }
}
