package net.pipe01.pinepartner.utils

import android.os.Build
import android.os.Bundle

inline fun <reified T> Bundle.getParcelableList(key: String): List<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getParcelableArray(key, T::class.java)?.toList()
    } else {
        (this.getParcelableArray(key) as? Array<T>)?.toList()
    }
}