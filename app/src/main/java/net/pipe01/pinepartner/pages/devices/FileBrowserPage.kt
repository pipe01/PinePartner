package net.pipe01.pinepartner.pages.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.components.Header
import net.pipe01.pinepartner.components.LoadingStandIn
import net.pipe01.pinepartner.devices.blefs.File
import net.pipe01.pinepartner.devices.blefs.joinPaths
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.utils.BoxWithFAB
import net.pipe01.pinepartner.utils.ExpandableFAB
import java.time.Instant

@Composable
fun FileBrowserPage(
    backgroundService: BackgroundService,
    deviceAddress: String,
    path: String,
    onOpenFolder: (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val files = remember { mutableStateListOf<File>() }
    var isLoading by remember { mutableStateOf(true) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        isLoading = true
        files.clear()
        files.addAll(backgroundService.listFiles(deviceAddress, path))
        isLoading = false
    }

    LaunchedEffect(path) {
        reload()
    }

    if (showCreateFolderDialog || showCreateFileDialog) {
        CreateDialog(
            title = if (showCreateFolderDialog) "Enter new folder name" else "Enter new file name",
            existingNames = files.map { it.name },
            onDismissRequest = {
                showCreateFolderDialog = false
                showCreateFileDialog = false
            },
            onCreate = { name ->
                val isFolder = showCreateFolderDialog
                showCreateFolderDialog = false
                showCreateFileDialog = false
                isLoading = true

                coroutineScope.launch {
                    if (isFolder) {
                        backgroundService.createFolder(deviceAddress, joinPaths(path, name))
                    } else {
                        backgroundService.writeFile(deviceAddress, joinPaths(path, name), ByteArray(0))
                    }
                    reload()
                }
            },
        )
    }

    Column(
        modifier = Modifier.scrollable(rememberScrollState(), Orientation.Vertical),
    ) {
        Header(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = "/$path",
        )

        LoadingStandIn(isLoading = isLoading) {
            BoxWithFAB(fab = {
                ExpandableFAB(
                    modifier = it,
                    icon = Icons.Outlined.Add,
                ) {
                    action(
                        icon = { Icon(Icons.Outlined.Folder, contentDescription = "Create folder") },
                        text = "Create folder",
                        onClick = { showCreateFolderDialog = true }
                    )
                    action(
                        icon = { Icon(Icons.Outlined.FileUpload, contentDescription = "Send file") },
                        text = "Send file",
                        onClick = { }
                    )
                    action(
                        icon = { Icon(Icons.Outlined.Description, contentDescription = "Create empty file") },
                        text = "Create empty file",
                        onClick = { showCreateFileDialog = true }
                    )
                }
            }) {
                FileList(
                    files = files
                        .filter { it.name != "." && (path != "" || it.name != "..") }
                        .sortedBy { it.name }
                        .sortedByDescending { it.isDirectory },
                    onOpenFolder = onOpenFolder,
                )
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<File>,
    onOpenFolder: (String) -> Unit = { },
) {
    Column(
        modifier = Modifier.padding(vertical = 16.dp),
    ) {
        for (file in files) {
            FileListItem(
                file = file,
                onOpen = {
                    if (file.isDirectory) {
                        onOpenFolder(file.fullPath)
                    }
                }
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: File,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (file.isDirectory) {
            Icon(Icons.Outlined.Folder, contentDescription = "Folder")
        } else {
            Icon(Icons.Outlined.Description, contentDescription = "File")
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(text = file.name)

        if (!file.isDirectory) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .alpha(0.6f),
                text = "${file.size} bytes",
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CreateDialog(
    title: String,
    existingNames: List<String>,
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    val isValid by remember { derivedStateOf { name.isNotBlank() && !existingNames.contains(name) } }

    val (focusRequester) = FocusRequester.createRefs()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onCreate(name) },
            ) {
                Text(text = "Create")
            }
        },
        text = {
            Column {
                Text(text = title)

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) onCreate(name) }),
                    modifier = Modifier.focusRequester(focusRequester)
                )

                if (existingNames.contains(name)) {
                    Text(
                        text = "Name already exists",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun FileListPreview() {
    FileList(
        files = listOf(
            File(".", "", true, Instant.now(), 0u),
            File("..", "", true, Instant.now(), 0u),
            File("test.txt", "test.txt", false, Instant.now(), 100u),
        )
    )
}
