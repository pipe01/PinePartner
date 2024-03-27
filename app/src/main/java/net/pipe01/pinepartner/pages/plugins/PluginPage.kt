package net.pipe01.pinepartner.pages.plugins

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import net.pipe01.pinepartner.scripting.BooleanType
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.scripting.EventSeverity
import net.pipe01.pinepartner.scripting.IntegerType
import net.pipe01.pinepartner.scripting.LogEvent
import net.pipe01.pinepartner.scripting.Parameter
import net.pipe01.pinepartner.scripting.Permission
import net.pipe01.pinepartner.scripting.StringType
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
    onError: (Error) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var plugin by remember { mutableStateOf<Plugin?>(null) }
    var paramValues by remember { mutableStateOf<Map<String, String>?>(null) }
    val events = remember { mutableStateListOf<LogEvent>() }

    LaunchedEffect(id) {
        plugin = BuiltInPlugins.get(id) ?: pluginDao.getById(id) ?: throw IllegalArgumentException("Plugin not found")
        paramValues = pluginDao
            .getParameterValues(id)
            ?.associateBy({ it.paramName }, { it.value })
            ?: emptyMap()

        while (true) {
            val resp = backgroundService.getPluginEvents(id, events.lastOrNull()?.time?.toEpochSecond(ZoneOffset.UTC) ?: 0)

            resp.fold(
                onSuccess = { events.addAll(it) },
                onFailure = { onError(Error("Failed to get plugin events", it)) }
            )

            delay(1000)
        }
    }

    LoadingStandIn(isLoading = plugin == null || paramValues == null) {
        Plugin(
            plugin = plugin!!,
            paramValues = paramValues!!,
            events = events,
            onRemove = {
                coroutineScope.launch {
                    backgroundService.deletePlugin(id).onFailure {
                        onError(Error("Failed to delete plugin", it))
                        return@launch
                    }

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

                    backgroundService.reloadPlugins().onFailure {
                        onError(Error("Failed to reload plugins", it))
                    }

                    Toast.makeText(context, "Plugin updated", Toast.LENGTH_SHORT).show()
                }
            },
            onViewCode = onViewCode,
            onSetParameter = { name, value ->
                val newParamValues = paramValues!!.toMutableMap()
                newParamValues[name] = value
                paramValues = newParamValues

                coroutineScope.launch {
                    pluginDao.setParameterValue(id, name, value)
                }
            }
        )
    }
}

@Composable
private fun Plugin(
    plugin: Plugin,
    paramValues: Map<String, String> = emptyMap(),
    events: List<LogEvent>,
    onRemove: () -> Unit = { },
    onUpdate: () -> Unit = { },
    onViewCode: () -> Unit = { },
    onSetParameter: (name: String, value: String) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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

        Property(name = "Parameters") {
            for (param in plugin.parameters) {
                val value = paramValues[param.name] ?: param.defaultValue ?: continue

                Parameter(
                    param = param,
                    value = value,
                    onSetValue = { onSetParameter(param.name, it) }
                )
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

@Composable
private fun Parameter(param: Parameter, value: String, onSetValue: (String) -> Unit) {
    Row(
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(40f),
            text = param.name,
            style = MaterialTheme.typography.titleMedium,
        )

        Row(modifier = Modifier.weight(60f)) {
            if (param.defaultValue != null) {
                FilledIconButton(onClick = {
                    onSetValue(param.defaultValue)
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset to default")
                }
            }

            when (param.type) {
                StringType -> TextField(
                    value = StringType.unmarshal(value),
                    onValueChange = { onSetValue(StringType.marshal(it)) },
                    singleLine = true,
                )

                IntegerType -> TextField(
                    value = IntegerType.unmarshal(value).toString(),
                    onValueChange = { onSetValue(IntegerType.marshal(it.filter(Char::isDigit).toInt())) },
                    singleLine = true,
                )

                BooleanType -> {
                    Switch(
                        checked = BooleanType.unmarshal(value),
                        onCheckedChange = { onSetValue(BooleanType.marshal(it)) },
                    )
                }
            }
        }
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
            parameters = listOf(
                Parameter(
                    name = "param1",
                    type = StringType,
                    defaultValue = "default",
                ),
                Parameter(
                    name = "param2",
                    type = IntegerType,
                    defaultValue = "123",
                ),
                Parameter(
                    name = "param3",
                    type = BooleanType,
                    defaultValue = "true",
                ),
            ),
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
            parameters = emptyList(),
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
            parameters = emptyList(),
            checksum = "",
            downloadUrl = "",
            enabled = true,
            isBuiltIn = false,
        ),
        events = emptyList(),
    )
}
