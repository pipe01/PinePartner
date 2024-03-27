package net.pipe01.pinepartner.utils.composables

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ErrorDialog(
    error: Error,
    onDismissRequest: () -> Unit,
    onTryAgain: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            if (error.cause != null) {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(error.cause!!.stackTraceToString()))
                    Toast.makeText(context, "Copied stack trace to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy to clipboard")
                }
            }
        },
        confirmButton = {
            onTryAgain?.let {
                TextButton(onClick = it) {
                    Text("Try again")
                }
            }
        },
        title = { Text(error.message ?: "An error occurred") },
        text = { Text(error.cause?.message ?: "An error occurred") }
    )
}