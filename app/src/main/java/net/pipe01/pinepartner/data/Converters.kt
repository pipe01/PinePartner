package net.pipe01.pinepartner.data

import androidx.room.TypeConverter
import net.pipe01.pinepartner.scripting.Parameter
import net.pipe01.pinepartner.scripting.Permission

class Converters {
    @TypeConverter
    fun fromPermissionSet(value: Set<Permission>): String {
        return value.sorted().joinToString(",")
    }

    @TypeConverter
    fun toPermissionSet(value: String): Set<Permission> {
        return if (value.isBlank())
            emptySet()
        else
            value.split(",").map { Permission.valueOf(it) }.toSet()
    }

    @TypeConverter
    fun fromParameterList(value: List<Parameter>): String {
        return value.joinToString("\n") { it.toString() }
    }

    @TypeConverter
    fun toParameterList(value: String): List<Parameter> {
        return if (value.isBlank())
            emptyList()
        else
            value.split("\n").mapNotNull { Parameter.parse(it) }
    }
}