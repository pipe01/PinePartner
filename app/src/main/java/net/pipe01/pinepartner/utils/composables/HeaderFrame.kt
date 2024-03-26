package net.pipe01.pinepartner.utils.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderFrame(header: String, content: @Composable (PaddingValues) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val collapsedTextSize = 20
    val expandedTextSize = 30
    val topAppBarTextSize = (collapsedTextSize + (expandedTextSize - collapsedTextSize) * (1 - scrollBehavior.state.collapsedFraction)).sp

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(text = header, fontSize = topAppBarTextSize) },
                scrollBehavior = scrollBehavior,
            )
        },
        content = content,
    )
}
