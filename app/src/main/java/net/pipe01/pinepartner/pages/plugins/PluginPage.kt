package net.pipe01.pinepartner.pages.plugins

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.components.LoadingStandIn
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.data.PluginDao
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.scripting.EventSeverity
import net.pipe01.pinepartner.scripting.LogEvent
import net.pipe01.pinepartner.scripting.Permission
import net.pipe01.pinepartner.scripting.downloadPlugin
import net.pipe01.pinepartner.service.BackgroundService
import java.time.ZoneOffset

@Composable
fun PluginPage(
    pluginDao: PluginDao,
    backgroundService: BackgroundService,
    id: String,
    onRemoved: () -> Unit,
    onViewCode: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var plugin by remember { mutableStateOf<Plugin?>(null) }
    val events = remember { mutableStateListOf<LogEvent>() }

    LaunchedEffect(id) {
        plugin = BuiltInPlugins.get(id) ?: pluginDao.getById(id) ?: throw IllegalArgumentException("Plugin not found")

        while (true) {
            val resp = backgroundService.getPluginEvents(id, events.lastOrNull()?.time?.toEpochSecond(ZoneOffset.UTC) ?: 0)

            events.addAll(resp)

            delay(1000)
        }
    }

    LoadingStandIn(isLoading = plugin == null) {
        Plugin(
            plugin = plugin!!,
            events = events,
            onRemove = {
                coroutineScope.launch {
                    backgroundService.deletePlugin(id)

                    onRemoved()
                }
            },
            onUpdate = {
                coroutineScope.launch {
                    val newPlugin = downloadPlugin(plugin!!.downloadUrl!!).copy(enabled = plugin!!.enabled)
                    if (newPlugin.id != plugin!!.id) {
                        Toast.makeText(context, "The plugin's ID has changed, cannot update", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (newPlugin.checksum == plugin!!.checksum) {
                        Toast.makeText(context, "Plugin is already up to date", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    pluginDao.update(newPlugin)
                    plugin = newPlugin

                    backgroundService.reloadPlugins()

                    Toast.makeText(context, "Plugin updated", Toast.LENGTH_SHORT).show()
                }
            },
            onViewCode = onViewCode,
        )
    }
}

@Composable
private fun Plugin(
    plugin: Plugin,
    events: List<LogEvent>,
    onRemove: () -> Unit = { },
    onUpdate: () -> Unit = { },
    onViewCode: () -> Unit = { },
) {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = plugin.name,
            style = MaterialTheme.typography.headlineLarge,
        )

        if (plugin.description != null) {
            Text(
                text = plugin.description,
            )
        }

        if (!plugin.isBuiltIn) {
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Button(
                    onClick = onRemove, //TODO: Ask for confirmation
                ) {
                    Text(text = "Remove plugin")
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = onUpdate) {
                    Text(text = "Update plugin")
                }
            }
        }
        Button(onClick = onViewCode) {
            Text(text = "View code")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Property("Author") { Text(text = plugin.author ?: "Unknown") }
        Property("ID") { Text(text = plugin.id) }

        Property("Permissions") {
            if (plugin.permissions.isEmpty()) {
                Text(
                    modifier = Modifier.alpha(0.6f),
                    text = "No permissions required"
                )
            } else {
                plugin.permissions
                    .sortedBy { it.title }
                    .forEach {
                        Text(text = "\u2022 " + it.title)
                    }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column {
            Text(
                text = "Events",
                style = MaterialTheme.typography.titleLarge,
            )

            events.reversed().forEach {
                SelectionContainer {
                    Text(buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = when (it.severity) {
                                    EventSeverity.ERROR, EventSeverity.FATAL -> MaterialTheme.colorScheme.error
                                    else -> Color.Unspecified
                                },
                                fontWeight = FontWeight.Black
                            )
                        ) {
                            append(
                                when (it.severity) {
                                    EventSeverity.INFO -> "INFO"
                                    EventSeverity.WARN -> "WARN"
                                    EventSeverity.ERROR -> "ERROR"
                                    EventSeverity.FATAL -> "FATAL"
                                }
                            )
                        }
                        append(" " + it.message)
                    })
                }
            }
        }
    }
}

@Composable
private fun Property(name: String, value: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
        )
        value()
    }
}

@Preview(showBackground = true)
@Composable
fun PluginPreview() {
    Plugin(
        plugin = Plugin(
            id = "preview-plugin",
            name = "Preview Plugin",
            description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
            author = "author",
            sourceCode = "",
            checksum = "",
            permissions = Permission.entries.toSet(),
            downloadUrl = "",
            enabled = true,
            isBuiltIn = false,
        ),
        events = emptyList(),
    )
}

@Preview(showBackground = true)
@Composable
fun PluginPreviewNoPermissions() {
    Plugin(
        plugin = Plugin(
            id = "preview-plugin",
            name = "Preview Plugin",
            description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
            author = "author",
            sourceCode = "",
            permissions = emptySet(),
            checksum = "",
            downloadUrl = "",
            enabled = true,
            isBuiltIn = false,
        ),
        events = emptyList(),
    )
}

@Preview(showBackground = true)
@Composable
fun PluginPreviewNoDescription() {
    Plugin(
        plugin = Plugin(
            id = "preview-plugin",
            name = "Preview Plugin",
            description = null,
            author = "author",
            sourceCode = "",
            permissions = emptySet(),
            checksum = "",
            downloadUrl = "",
            enabled = true,
            isBuiltIn = false,
        ),
        events = emptyList(),
    )
}
