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
import androidx.compose.ui.unit.dp
import com.wakaztahir.codeeditor.highlight.model.CodeLang
import com.wakaztahir.codeeditor.highlight.prettify.PrettifyParser
import com.wakaztahir.codeeditor.highlight.theme.CodeThemeType
import com.wakaztahir.codeeditor.highlight.utils.parseCodeAsAnnotatedString
import net.pipe01.pinepartner.data.Plugin
import net.pipe01.pinepartner.data.PluginDao
import net.pipe01.pinepartner.scripting.BuiltInPlugins

@Composable
fun CodeViewerPage(pluginDao: PluginDao, pluginId: String) {
    var plugin by remember { mutableStateOf<Plugin?>(null) }

    LaunchedEffect(pluginId) {
        plugin = BuiltInPlugins.get(pluginId) ?: pluginDao.getById(pluginId)
    }

    val parser = remember { PrettifyParser() }
    val themeState by remember { mutableStateOf(CodeThemeType.Default) }
    val theme = remember(themeState) { themeState.theme() }

    if (plugin != null) {
        val parsedCode = remember {
            parseCodeAsAnnotatedString(
                parser = parser,
                theme = theme,
                lang = CodeLang.JavaScript,
                code = plugin!!.sourceCode,
            )
        }

        Column(
            modifier = Modifier
                .padding(5.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            
            SelectionContainer {
                Text(text = parsedCode)
            }
        }
    }
}