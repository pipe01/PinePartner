package net.pipe01.pinepartner.devices

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class WatchState(val isConnected: Boolean, val firmwareVersion: String, val batteryLevel: Float) : Parcelable