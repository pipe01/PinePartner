package net.pipe01.pinepartner.pages.devices

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.components.Header
import net.pipe01.pinepartner.components.LoadingStandIn
import net.pipe01.pinepartner.devices.blefs.File
import net.pipe01.pinepartner.devices.blefs.joinPaths
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.service.TransferProgress
import net.pipe01.pinepartner.utils.composables.BoxWithFAB
import net.pipe01.pinepartner.utils.composables.ExpandableFAB
import net.pipe01.pinepartner.utils.composables.PopupDialog
import net.pipe01.pinepartner.utils.composables.ProgressIndicator
import java.time.Instant
import kotlin.random.Random

@Composable
fun FileBrowserPage(
    backgroundService: BackgroundService,
    deviceAddress: String,
    path: String,
    onOpenFolder: (String) -> Unit,
    onError: (Error) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val files = remember { mutableStateListOf<File>() }
    var isLoading by remember { mutableStateOf(true) }

    val selectedFiles = remember { mutableStateListOf<File>() }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showUploadFileDialog by remember { mutableStateOf(false) }
    var showFlashResourcesDialog by remember { mutableStateOf(false) }
    var showOpenFileDialog by remember { mutableStateOf<File?>(null) }
    var showConfirmDeleteDialog by remember { mutableStateOf<List<File>?>(null) }

    suspend fun reload() {
        isLoading = true
        files.clear()

        backgroundService.listFiles(deviceAddress, path).fold(
            onSuccess = { files.addAll(it) },
            onFailure = { onError(Error("Failed to list files", it)) },
        )

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
                    }.onFailure {
                        onError(Error("Failed to create ${if (isFolder) "folder" else "file"}", it))
                    }
                    reload()
                }
            },
        )
    } else if (showUploadFileDialog || showFlashResourcesDialog) {
        UploadDialog(
            backgroundService = backgroundService,
            deviceAddress = deviceAddress,
            isExternalResources = showFlashResourcesDialog,
            path = path,
            onDone = {
                showUploadFileDialog = false
                showFlashResourcesDialog = false

                coroutineScope.launch { reload() }
            },
            onCancel = {
                showUploadFileDialog = false
                showFlashResourcesDialog = false
            },
            onError = onError,
        )
    } else if (showOpenFileDialog != null) {
        FileActionsDialog(
            file = showOpenFileDialog!!,
            onDismissRequest = { showOpenFileDialog = null },
            onDelete = {
                showConfirmDeleteDialog = listOf(showOpenFileDialog!!)
                showOpenFileDialog = null
            },
        )
    } else if (showConfirmDeleteDialog != null) {
        ConfirmDeleteDialog(
            files = showConfirmDeleteDialog!!,
            onDone = {
                showConfirmDeleteDialog = null

                coroutineScope.launch {
                    reload()
                }
            },
            onCancel = {
                showConfirmDeleteDialog = null
            },
            onDeleteFile = {
                backgroundService.deleteFile(deviceAddress, it.fullPath).onFailure {
                    onError(Error("Failed to delete file", it))
                }
            },
        )
    }

    Box {
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
                            icon = { Icon(Icons.Outlined.Description, contentDescription = "Create empty file") },
                            text = "Create empty file",
                            onClick = { showCreateFileDialog = true }
                        )
                        action(
                            icon = { Icon(Icons.Outlined.FileUpload, contentDescription = "Send file") },
                            text = "Send file",
                            onClick = { showUploadFileDialog = true }
                        )
                        action(
                            icon = { Icon(Icons.Outlined.Archive, contentDescription = "Flash resources") },
                            text = "Flash resources",
                            onClick = { showFlashResourcesDialog = true }
                        )
                    }
                }) {
                    FileList(
                        files = files
                            .filter { it.name != "." && (path != "" || it.name != "..") }
                            .sortedBy { it.name }
                            .sortedByDescending { it.isDirectory },
                        selected = selectedFiles,
                        onOpenFolder = { onOpenFolder(it.fullPath) },
                        onOpenFile = { showOpenFileDialog = it },
                        onToggleSelect = {
                            if (selectedFiles.contains(it)) {
                                selectedFiles.remove(it)
                            } else {
                                selectedFiles.add(it)
                            }
                        }
                    )
                }
            }
        }

        if (selectedFiles.isNotEmpty()) {
            ActionBar(
                selected = selectedFiles,
                onCancel = { selectedFiles.clear() },
                onDelete = {
                    showConfirmDeleteDialog = selectedFiles.toList()
                    selectedFiles.clear()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionBar(
    selected: List<File>,
    onCancel: () -> Unit = { },
    onDelete: () -> Unit = { },
) {
    TopAppBar(
        title = { Text("Selected ${selected.size} files") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
    )
}

@Preview
@Composable
private fun ActionBarPreview() {
    ActionBar(selected = emptyList())
}

@Composable
private fun FileList(
    files: List<File>,
    selected: List<File>,
    onOpenFolder: (File) -> Unit = { },
    onOpenFile: (File) -> Unit = { },
    onToggleSelect: (File) -> Unit = { },
) {
    Column(
        modifier = Modifier.padding(vertical = 16.dp),
    ) {
        for (file in files) {
            FileListItem(
                file = file,
                isSelected = selected.contains(file),
                isSelecting = selected.isNotEmpty(),
                onOpen = {
                    if (file.isDirectory) {
                        onOpenFolder(file)
                    } else {
                        onOpenFile(file)
                    }
                },
                onToggleSelect = {
                    if (file.name != "." && file.name != "..") {
                        onToggleSelect(file)
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun FileListPreview() {
    FileList(
        files = listOf(
            File(".", "", true, Instant.now(), 0u),
            File("..", "", true, Instant.now(), 0u),
            File("test.txt", "test.txt", false, Instant.now(), 100u),
            File("very-very-very-very-very-very-long-name.txt", "test.txt", false, Instant.now(), 100u),
        ),
        selected = emptyList(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: File,
    isSelected: Boolean,
    isSelecting: Boolean,
    onOpen: () -> Unit = { },
    onToggleSelect: () -> Unit = { },
) {
    Row(
        modifier = Modifier
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .height(60.dp)
            .combinedClickable(
                onClick = {
                    if (isSelecting)
                        onToggleSelect()
                    else
                        onOpen()
                },
                onLongClick = { onToggleSelect() },
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground

        if (file.isDirectory) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = "Folder",
                tint = color,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = "File",
                tint = color,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            modifier = Modifier.weight(1f),
            text = file.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color,
        )

        if (!file.isDirectory) {
            Text(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .alpha(0.6f),
                text = "${file.size} bytes",
                color = color,
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

    val isValid by remember {
        derivedStateOf {
            name.isNotBlank() && !existingNames.contains(name) && name != "." && name != ".."
        }
    }

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

@Composable
private fun UploadDialog(
    deviceAddress: String,
    path: String,
    backgroundService: BackgroundService,
    isExternalResources: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onError: (Error) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var chosenFileUri by remember { mutableStateOf<Uri?>(null) }

    val jobId = remember { Random.nextInt() }
    var lastProgress by remember { mutableStateOf<TransferProgress?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { fileUri ->
        if (fileUri != null) {
            chosenFileUri = fileUri

            coroutineScope.launch {
                if (isExternalResources) {
                    backgroundService.uploadResources(jobId, deviceAddress, fileUri)
                } else {
                    backgroundService.sendFile(jobId, deviceAddress, path, fileUri)
                }.onFailure {
                    onError(Error("Failed to upload file", it))
                }

                onDone()
            }

            coroutineScope.launch {
                while (true) {
                    val progress = backgroundService.getTransferProgress(jobId)
                    if (progress?.isDone == true) {
                        break
                    }

                    lastProgress = progress

                    delay(500)
                }
            }
        } else {
            onCancel()
        }
    }

    LaunchedEffect(Unit) {
        pickFileLauncher.launch(if (isExternalResources) "application/zip" else "*/*")
    }

    if (chosenFileUri != null) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            text = {
                Column {
                    ProgressIndicator("Uploading", lastProgress)

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = {
                            backgroundService.cancelTransfer(jobId).onFailure {
                                onError(Error("Failed to cancel transfer", it))
                            }
                        },
                    ) {
                        Text(text = "Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun FileActionsDialog(
    file: File,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    PopupDialog(
        title = {
            SelectionContainer {
                Text(text = file.name)
            }
        },
        onDismissRequest = onDismissRequest,
    ) {
        action(Icons.Filled.Download, "Download to phone") {

        }
        action(Icons.Filled.Delete, "Delete", onDelete)
    }
}

@Preview(widthDp = 320, heightDp = 640)
@Composable
private fun FileActionsDialogPreview() {
    FileActionsDialog(
        file = File("test.txt", "/foo/bar/test.txt", false, Instant.now(), 100u),
        onDismissRequest = { },
        onDelete = { },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    files: List<File>,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onDeleteFile: suspend (File) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        title = {
            if (!isLoading) {
                Text(text = "Delete ${files.size} file${if (files.size == 1) "" else "s"}?")
            }
        },
        onDismissRequest = {
            if (!isLoading) {
                onCancel()
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    isLoading = true

                    coroutineScope.launch {
                        files.forEach { onDeleteFile(it) }

                        onDone()
                    }
                },
            ) {
                Text(text = "Delete")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoading,
                onClick = onCancel,
            ) {
                Text(text = "Cancel")
            }
        },
        text = {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Deleting files...")
                }
            }
        }
    )
}

