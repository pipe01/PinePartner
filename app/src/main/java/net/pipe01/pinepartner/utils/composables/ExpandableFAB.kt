package net.pipe01.pinepartner.utils.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

interface ExpandableFABScope {
    @Composable
    fun action(icon: @Composable () -> Unit, text: String, onClick: () -> Unit)
}

@Composable
fun ExpandableFAB(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    content: @Composable ExpandableFABScope.() -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    //TODO: Add animations

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (isExpanded) {
            content(object : ExpandableFABScope {
                @Composable
                override fun action(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.background(MaterialTheme.colorScheme.background),
                            text = text,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        FloatingActionButton(
                            modifier = Modifier.scale(0.8f),
                            onClick = {
                                isExpanded = false
                                onClick()
                            },
                            shape = CircleShape,
                        ) {
                            icon()
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            })
        }

        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
        ) {
            Icon(
                imageVector = icon,
                modifier = Modifier.rotate(if (isExpanded) 45f else 0f),
                contentDescription = null, //TODO: Fill this
            )
        }
    }
}