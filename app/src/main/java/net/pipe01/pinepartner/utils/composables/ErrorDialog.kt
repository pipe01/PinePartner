package net.pipe01.pinepartner.utils.composables

import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import net.pipe01.pinepartner.utils.PineError

@Composable
fun ErrorDialog(
    error: PineError,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = {
            error.onDismiss?.invoke()
            onDismissRequest()
        },
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
            error.onTryAgain?.let {
                TextButton(onClick = it) {
                    Text("Try again")
                }
            }
        },
        title = {
            Text(
                color = MaterialTheme.colorScheme.error,
                text = error.message ?: "An error occurred",
            )
        },
        text = {
            SelectionContainer {
                Text(error.cause?.message ?: "An error occurred")
            }
        }
    )
}

@Preview
@Composable
fun ErrorDialogPreview() {
    ErrorDialog(PineError("An error occurred", Exception("This is a test exception")), {})
}
