package net.pipe01.pinepartner.pages.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.components.LoadingStandIn
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.utils.BoxWithFAB
import net.pipe01.pinepartner.utils.HeaderFrame

@Composable
fun PluginsPage(
    db: AppDatabase,
    backgroundService: BackgroundService,
    onPluginClicked: (Plugin) -> Unit,
    onImportPlugin: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    val plugins = remember { mutableStateListOf<Plugin>() }
    var loadedPlugins by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        plugins.addAll(BuiltInPlugins.plugins)

        coroutineScope.launch {
            plugins.addAll(db.pluginDao().getAll())
            loadedPlugins = true
        }
    }

    BoxWithFAB(fab = {
        FloatingActionButton(
            modifier = it,
            onClick = onImportPlugin,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add new script")
        }
    }) {
        HeaderFrame(header = "Plugins") {
            LoadingStandIn(isLoading = !loadedPlugins) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState),
                ) {
                    PluginList(
                        backgroundService = backgroundService,
                        plugins = plugins,
                        onPluginClicked = onPluginClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginList(
    backgroundService: BackgroundService,
    plugins: MutableList<Plugin>,
    onPluginClicked: (Plugin) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    Column {
        for (plugin in plugins) {
            Spacer(modifier = Modifier.height(16.dp))

            PluginItem(
                plugin = plugin,
                onClick = { onPluginClicked(plugin) },
                onEnabledChange = { enabled ->
                    coroutineScope.launch {
                        if (enabled)
                            backgroundService.enablePlugin(plugin.id)
                        else
                            backgroundService.disablePlugin(plugin.id)

                        val index = plugins.indexOf(plugin)

                        plugins[index] = plugin.copy(enabled = enabled)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PluginItem(
    plugin: Plugin,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 20.sp,
                )

                if (plugin.description != null) {
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = plugin.description,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                if (plugin.isBuiltIn) {
                    Spacer(modifier = Modifier.height(4.dp))

                    AssistChip(
                        onClick = onClick,
                        label = { Text("Built-in") },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    )
                }
            }

            if (!plugin.isBuiltIn) {
                Column {
                    Switch(
                        checked = plugin.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PluginItemPreview() {
    Column(
        modifier = Modifier.width(400.dp)
    ) {
        for (i in 0..2) {
            val plugin = Plugin(
                id = "id$i",
                name = "Plugin $i",
                description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam vitae urna dapibus, pretium nulla at, ullamcorper risus. Sed nec nulla rutrum, tristique dolor ut, congue justo.",
                author = null,
                sourceCode = "",
                checksum = "",
                permissions = emptySet(),
                downloadUrl = null,
                enabled = i % 2 == 1,
                isBuiltIn = i % 2 == 0,
            )

            PluginItem(
                plugin = plugin,
                onEnabledChange = { },
                onClick = { },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
