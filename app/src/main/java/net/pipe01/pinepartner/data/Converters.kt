package net.pipe01.pinepartner.data

import androidx.room.TypeConverter
import net.pipe01.pinepartner.scripting.Permission

class Converters {
    @TypeConverter
    fun fromPermissionSet(value: Set<Permission>): String {
        return value.sorted().joinToString(",")
    }

    @TypeConverter
    fun toPermissionSet(value: String): Set<Permission> {
        return value.split(",").map { Permission.valueOf(it) }.toSet()
    }
}