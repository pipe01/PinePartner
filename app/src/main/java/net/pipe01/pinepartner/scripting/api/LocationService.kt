package net.pipe01.pinepartner.scripting.api

import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import net.pipe01.pinepartner.scripting.api.adapters.LocationAdapter
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.annotations.JSFunction

class LocationService : ApiScriptableObject(LocationService::class) {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    fun init(fusedLocationClient: FusedLocationProviderClient) {
        this.fusedLocationClient = fusedLocationClient
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun getCurrent(priorityValue: Any): LocationAdapter {
        val priority = if (Undefined.isUndefined(priorityValue))
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        else when (priorityValue) {
            "highAccuracy" -> Priority.PRIORITY_HIGH_ACCURACY
            "balanced" -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            "lowPower" -> Priority.PRIORITY_LOW_POWER
            "passive" -> Priority.PRIORITY_PASSIVE
            else -> throw Context.throwAsScriptRuntimeEx(IllegalArgumentException("Invalid priority value"))
        }

        val task = fusedLocationClient.getCurrentLocation(priority, null)

        val location = runBlocking {
            task.await()
        }

        return newObject(LocationAdapter::class).apply {
            init(location)
        }
    }

    @SuppressLint("MissingPermission")
    @JSFunction
    fun getLast(): LocationAdapter? {
        val task = fusedLocationClient.lastLocation

        val location = runBlocking {
            task.await()
        } ?: return null

        return newObject(LocationAdapter::class).apply {
            init(location)
        }
    }
}