package net.pipe01.pinepartner.devices.externalResources

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.pipe01.pinepartner.devices.Device
import net.pipe01.pinepartner.devices.blefs.BLEFSException
import net.pipe01.pinepartner.devices.blefs.LittleFSError
import net.pipe01.pinepartner.devices.blefs.cleanPath
import net.pipe01.pinepartner.devices.blefs.createAllFolders
import net.pipe01.pinepartner.devices.blefs.deleteFile
import net.pipe01.pinepartner.devices.blefs.writeFile
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.unzip
import java.io.ByteArrayInputStream
import java.io.InputStream

private const val TAG = "ExternalResources"

@Serializable
private data class ResourcesManifest(
    val resources: List<Resource>,
    val obsolete_files: List<ObsoleteFile>,
) {
    @Serializable
    data class Resource(
        val filename: String,
        val path: String,
    )

    @Serializable
    data class ObsoleteFile(
        val path: String,
        val since: String,
    )
}

suspend fun Device.uploadExternalResources(zipStream: InputStream, coroutineScope: CoroutineScope, onProgress: (TransferProgress) -> Unit) {
    val files = zipStream.unzip()

    Log.d(TAG, "Unzipped ${files.size} files: ${files.map { it.key }.joinToString(", ")}")

    val manifestJson = files["resources.json"]?.decodeToString() ?: throw IllegalArgumentException("manifest.json not found in zip")
    val manifest = Json.decodeFromString<ResourcesManifest>(manifestJson)

    val forSureExistsPaths = mutableSetOf<String>()

    var cumProgress = 0f

    manifest.resources.forEach { res ->
        val data = files[res.filename] ?: throw IllegalArgumentException("Resource file ${res.filename} not found in zip")
        val path = cleanPath(res.path)

        Log.d(TAG, "Uploading ${res.filename} to ${path} (${data.size} bytes)")

        val folder = path.substringBeforeLast('/')
        if (folder != "" && !forSureExistsPaths.contains(folder)) {
            createAllFolders(folder, coroutineScope)
            forSureExistsPaths.add(folder)
        }

        writeFile(path, ByteArrayInputStream(data), data.size, coroutineScope) {
            onProgress(
                TransferProgress(
                    totalProgress = cumProgress + it.totalProgress / manifest.resources.size,
                    bytesPerSecond = it.bytesPerSecond,
                    timeLeft = null,
                    isDone = false,
                )
            )
        }

        cumProgress += 1f / manifest.resources.size

        delay(200) // Prevent overwhelming the watch
    }

    manifest.obsolete_files.forEach {
        Log.d(TAG, "Deleting obsolete file ${it.path}")

        try {
            this.deleteFile(it.path, coroutineScope)
        } catch (e: BLEFSException) {
            if (e.errorCode != LittleFSError.NOENT) {
                throw e
            }
        }

        delay(200)
    }
}