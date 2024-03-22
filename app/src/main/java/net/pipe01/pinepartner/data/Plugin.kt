package net.pipe01.pinepartner.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import net.pipe01.pinepartner.scripting.Permission
import net.pipe01.pinepartner.utils.md5
import java.util.Scanner

class PluginParseException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

@Entity
data class Plugin @JvmOverloads constructor(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val author: String?,
    val sourceCode: String,
    val checksum: String,
    val permissions: Set<Permission>,
    val downloadUrl: String?,
    val enabled: Boolean,
    @Ignore val isBuiltIn: Boolean = false,
) {
    companion object {
        fun parse(source: String, downloadUrl: String? = null, isBuiltIn: Boolean = false): Plugin {
            var name: String? = null
            var id: String? = null
            var description: String? = null
            var author: String? = null
            var permissions = mutableSetOf<Permission>()

            var foundFooter = false

            Scanner(source).use { scanner ->
                var readLines = 0

                if (!scanner.hasNextLine() || scanner.nextLine() != "// ==PinePartner Plugin==") {
                    throw PluginParseException("Missing plugin header")
                }

                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine()
                    if (!line.startsWith("//")) {
                        break
                    }

                    if (++readLines > 100) {
                        throw PluginParseException("Too many lines in plugin header")
                    }

                    if (line == "// ==/PinePartner Plugin==") {
                        foundFooter = true
                        break
                    }

                    val rest = line.removePrefix("//").trim()
                    if (rest.isBlank()) {
                        continue
                    }

                    val spaceIdx = rest.indexOf(' ')
                    if (spaceIdx == -1) {
                        throw PluginParseException("Invalid plugin header line: $line")
                    }

                    val key = rest.take(spaceIdx)
                    val value = rest.substring(spaceIdx + 1)

                    when (key) {
                        "@id" -> id = value
                        "@name" -> name = value
                        "@description" -> description = value
                        "@author" -> author = value
                        "@permission" -> permissions.add(Permission.valueOf(value))
                        else -> throw PluginParseException("Unknown plugin header key: $key")
                    }
                }
            }

            if (!foundFooter) {
                throw PluginParseException("Missing plugin footer")
            }

            if (name == null) {
                throw PluginParseException("Missing plugin name")
            }
            if (id == null) {
                throw PluginParseException("Missing plugin id")
            }
            if (!id!!.matches(Regex("^[a-zA-Z0-9\\-_.]+$"))) {
                throw PluginParseException("Invalid plugin id")
            }

            return Plugin(
                id = id!!,
                name = name!!,
                description = description,
                author = author,
                sourceCode = source,
                checksum = source.md5(),
                permissions = permissions,
                enabled = false,
                downloadUrl = downloadUrl,
                isBuiltIn = isBuiltIn,
            )
        }
    }
}
