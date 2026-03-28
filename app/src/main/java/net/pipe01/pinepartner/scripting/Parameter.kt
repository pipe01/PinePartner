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

            val name = parts[1]
            if (!name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                return null
            }

            val type = when (parts[0]) {
                StringType.name -> StringType
                SecretType.name -> SecretType
                IntegerType.name -> IntegerType
                BooleanType.name -> BooleanType
                else -> return null
            }

            return Parameter(
                name = name,
                type = type,
                defaultValue = parts.getOrNull(2) ?: type.marshal(type.default)
            )
        }
    }

    override fun toString(): String {
        return "${type.name} $name $defaultValue"
    }
}

interface ParameterType {
    val default: Any

    val name: String

    fun validate(value: Any): Boolean

    fun marshal(value: Any): String
    fun unmarshal(str: String): Any
}

object StringType : ParameterType {
    override val default = ""
    override val name = "string"

    override fun validate(value: Any) = value is String

    override fun marshal(value: Any) = value as String

    override fun unmarshal(str: String) = str
}

object SecretType : ParameterType {
    override val default = ""
    override val name = "secret"

    override fun validate(value: Any) = value is String

    override fun marshal(value: Any) = value as String

    override fun unmarshal(str: String) = str
}

object IntegerType : ParameterType {
    override val default = 0
    override val name = "int"

    override fun validate(value: Any) = value is Int

    override fun marshal(value: Any) = value.toString()

    override fun unmarshal(str: String) = str.toInt()
}

object BooleanType : ParameterType {
    override val default = false
    override val name = "boolean"

    override fun validate(value: Any) = value is Boolean

    override fun marshal(value: Any) = value.toString()

    override fun unmarshal(str: String) = str.toBoolean()
}