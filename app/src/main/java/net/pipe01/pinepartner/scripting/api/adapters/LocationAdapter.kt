package net.pipe01.pinepartner.scripting.api.adapters

import android.location.Location
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import org.mozilla.javascript.annotations.JSGetter

class LocationAdapter : ApiScriptableObject(LocationAdapter::class) {
    private lateinit var location: Location

    fun init(location: Location) {
        this.location = location
    }

    @JSGetter
    fun getLatitude() = location.latitude

    @JSGetter
    fun getLongitude() = location.longitude

    @JSGetter
    fun getAltitude() = if (location.hasAltitude()) location.altitude else null

    @JSGetter
    fun getAccuracy() = if (location.hasAccuracy()) location.accuracy else null
}