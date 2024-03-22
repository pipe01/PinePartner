package net.pipe01.pinepartner.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Header(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).then(modifier),
        text = text,
        fontSize = 30.sp,
    )
}