package net.pipe01.pinepartner.pages.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.dp
import com.wakaztahir.codeeditor.highlight.model.CodeLang
import com.wakaztahir.codeeditor.highlight.prettify.PrettifyParser
import com.wakaztahir.codeeditor.highlight.theme.CodeThemeType
import com.wakaztahir.codeeditor.highlight.utils.parseCodeAsAnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.components.LoadingStandIn
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.data.PluginDao
import net.pipe01.pinepartner.scripting.BuiltInPlugins

@Composable
fun CodeViewerPage(pluginDao: PluginDao, pluginId: String) {
    var plugin by remember { mutableStateOf<Plugin?>(null) }

    val parser = remember { PrettifyParser() }
    val themeState by remember { mutableStateOf(CodeThemeType.Default) }
    val theme = remember(themeState) { themeState.theme() }
    var parsedCode by remember { mutableStateOf<AnnotatedString?>(null) }

    LaunchedEffect(pluginId) {
        plugin = BuiltInPlugins.get(pluginId) ?: pluginDao.getById(pluginId)

        CoroutineScope(Dispatchers.IO).launch {
            parsedCode = parseCodeAsAnnotatedString(
                parser = parser,
                theme = theme,
                lang = CodeLang.JavaScript,
                code = plugin!!.sourceCode,
            )
        }
    }

    LoadingStandIn(isLoading = plugin == null || parsedCode == null) {
        Column(
            modifier = Modifier
                .padding(5.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val fontFamily = FontFamily(Typeface(android.graphics.Typeface.MONOSPACE))

            SelectionContainer {
                Text(
                    text = parsedCode!!,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}