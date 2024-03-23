package net.pipe01.pinepartner.scripting

data class Parameter(
    val name: String,
    val type: ParameterType,
    val defaultValue: String?,
) {
    companion object {
        fun parse(str: String): Parameter? {
            val parts = str.split(" ")

            if (parts.size < 2 || parts.size > 3) {
                return null
            }

            val name = parts[0]
            if (!name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                return null
            }

            return Parameter(
                name = parts[0],
                type = when (parts[1]) {
                    "string" -> StringType
                    "int" -> IntegerType
                    "boolean" -> BooleanType
                    else -> return null
                },
                defaultValue = parts.getOrNull(2)
            )
        }
    }

    override fun toString(): String {
        val typeName = when (type) {
            StringType -> "string"
            IntegerType -> "int"
            BooleanType -> "bool"
            else -> throw IllegalStateException("Unknown type")
        }

        return "$name $typeName${if (defaultValue != null) " $defaultValue" else ""}"
    }
}

interface ParameterType {
    fun validate(value: Any): Boolean

    fun marshal(value: Any): String
    fun unmarshal(str: String): Any
}

object StringType : ParameterType {
    override fun validate(value: Any) = value is String

    override fun marshal(value: Any) = value as String

    override fun unmarshal(str: String) = str
}

object IntegerType : ParameterType {
    override fun validate(value: Any) = value is Int

    override fun marshal(value: Any) = value.toString()

    override fun unmarshal(str: String) = str.toInt()
}

object BooleanType : ParameterType {
    override fun validate(value: Any) = value is Boolean

    override fun marshal(value: Any) = value.toString()

    override fun unmarshal(str: String) = str.toBoolean()
}