package net.pipe01.pinepartner.data

import androidx.room.Entity

@Entity(primaryKeys = ["pluginId", "paramName"])
data class ParameterValue(
    val pluginId: String,
    val paramName: String,
    val value: String,
)
