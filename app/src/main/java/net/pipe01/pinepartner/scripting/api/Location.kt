package net.pipe01.pinepartner.scripting.api

import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import net.pipe01.pinepartner.scripting.api.adapters.LocationAdapter
import org.mozilla.javascript.annotations.JSGetter

class Location : ApiScriptableObject(CLASS_NAME) {
    companion object {
        const val CLASS_NAME = "Location"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    fun init(fusedLocationClient: FusedLocationProviderClient) {
        this.fusedLocationClient = fusedLocationClient
    }

    @SuppressLint("MissingPermission")
    @JSGetter
    fun getCurrent(): LocationAdapter {
        val task = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)

        val location = runBlocking {
            task.await()
        }

        return newObject<LocationAdapter>(LocationAdapter.CLASS_NAME).apply {
            init(location)
        }
    }
}