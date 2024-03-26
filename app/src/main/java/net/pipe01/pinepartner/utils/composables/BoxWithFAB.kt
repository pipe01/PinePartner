package net.pipe01.pinepartner.utils.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BoxWithFAB(fab: @Composable (Modifier) -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        content()

        fab(Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}