package net.pipe01.pinepartner.pages.plugins

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.data.PluginDao
import net.pipe01.pinepartner.scripting.Permission
import net.pipe01.pinepartner.scripting.downloadPlugin
import net.pipe01.pinepartner.utils.composables.BoxWithFAB
import net.pipe01.pinepartner.utils.composables.HeaderFrame

@Composable
fun ImportPluginPage(
    pluginDao: PluginDao,
    onDone: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var downloadedPlugin by remember { mutableStateOf<Plugin?>(null) }

    HeaderFrame(header = "Import Plugin") {
        val sizeModifier = Modifier
            .fillMaxWidth()
            .padding(it)
            .padding(top = 20.dp)
            .padding(horizontal = 16.dp)

        if (downloadUrl == null) {
            DataStep(
                modifier = sizeModifier,
            ) { url ->
                downloadUrl = url
            }
        } else if (downloadedPlugin == null) {
            DownloadStep(
                modifier = sizeModifier,
                url = downloadUrl!!,
                onDone = {
                    downloadedPlugin = it
                },
            )
        } else {
            ImportStep(
                modifier = sizeModifier,
                plugin = downloadedPlugin!!,
                onImport = {
                    coroutineScope.launch {
                        pluginDao.insert(downloadedPlugin!!)

                        onDone()
                    }
                },
            )
        }
    }
}

@Composable
private fun DataStep(
    modifier: Modifier,
    onImportUrl: (String) -> Unit,
) {
    Column(modifier = modifier) {
        var url by remember { mutableStateOf("") }
        Text(
            text = "Import from URL:",
            style = MaterialTheme.typography.titleLarge,
        )

        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            label = { Text("URL") },
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            modifier = Modifier.align(Alignment.End),
            onClick = { onImportUrl(url) },
            enabled = url.isNotEmpty(),
        ) {
            Text("Import")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Import from QR code:",
            style = MaterialTheme.typography.titleLarge,
        )

        Button(
            onClick = { /*TODO*/ },
            enabled = false,
        ) {
            Text(text = "Scan QR code")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DataStepPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        DataStep(
            modifier = Modifier,
            onImportUrl = { },
        )
    }
}

@Composable
private fun DownloadStep(modifier: Modifier, url: String, onDone: (Plugin) -> Unit) {
    LaunchedEffect(url) {
        val plugin = downloadPlugin(url)

        onDone(plugin)
    }

    Box(
        modifier = modifier,
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ImportStep(modifier: Modifier, plugin: Plugin, onImport: () -> Unit) {
    BoxWithFAB(fab = {
        ExtendedFloatingActionButton(
            modifier = it,
            onClick = onImport,
            icon = { Icon(Icons.Filled.Add, "Import") },
            text = { Text(text = "Import") },
        )
    }) {
        Column(modifier = modifier) {
            Text(
                text = "Downloaded plugin: ${plugin.name}",
                style = MaterialTheme.typography.titleLarge,
            )

            if (plugin.description != null) {
                Text(
                    text = "${plugin.description}",
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (plugin.permissions.isEmpty()) {
                Text(
                    text = "This plugin does not require any permissions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "This plugin requires the following permissions:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )

                for (permission in plugin.permissions.sortedBy { it.title }) {
                    //TODO: Explain what each permission means and its risks
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = permission.title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportStepPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        ImportStep(
            modifier = Modifier,
            plugin = Plugin(
                id = "test-plugin",
                name = "Test Plugin",
                description = "This is a test plugin",
                author = "Plugin author",
                sourceCode = "",
                checksum = "",
                permissions = Permission.entries.toSet(),
                parameters = emptyList(),
                downloadUrl = "",
                enabled = false,
                isBuiltIn = false,
            ),
            onImport = { },
        )
    }
}
