package net.pipe01.pinepartner.pages.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.utils.AppInfo
import net.pipe01.pinepartner.utils.AppInfoCache

data class InstalledApp(val info: ApplicationInfo, val name: String, val packageName: String)

@SuppressLint("QueryPermissionsNeeded")
@Composable
fun NotificationSettingsPage(db: AppDatabase) {
    val context = LocalContext.current

    val installedApps = remember { mutableStateListOf<AppInfo>() }
    val enabledPackages = remember { mutableStateListOf<String>() }

    fun setEnabled(packageName: String, enabled: Boolean) {
        if (enabled) {
            enabledPackages.add(packageName)
        } else {
            enabledPackages.remove(packageName)
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (enabled) {
                db.allowedNotifAppDao().add(packageName)
            } else {
                db.allowedNotifAppDao().remove(packageName)
            }
        }
    }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            enabledPackages.addAll(db.allowedNotifAppDao().getAll().map { it.packageName })

            installedApps.addAll(
                context.packageManager
                    .getInstalledApplications(PackageManager.GET_META_DATA)
                    //.take(50)
                    .map {
                        AppInfoCache.getAppInfo(context.packageManager, it.packageName)!!
                    }
                    .sortedBy { it.label.lowercase() }
                    .sortedByDescending { enabledPackages.contains(it.fullInfo.packageName) }
            )
        }
    }

    Column {
        Button(onClick = {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            ContextCompat.startActivity(context, intent, null)
        }) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Open Android notification settings",
                textAlign = TextAlign.Center,
            )
        }

        if (installedApps.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            var filter by remember { mutableStateOf("") }

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Filter") },
                singleLine = true,
            )

            val filteredApps by remember(filter) {
                derivedStateOf {
                    installedApps.filter { it.label.contains(filter, ignoreCase = true) }
                }
            }

            LazyColumn {
                items(filteredApps.size) { index ->
                    val app = filteredApps[index]

                    AppRow(
                        app = app,
                        enabled = enabledPackages.contains(app.fullInfo.packageName),
                        onEnabledChange = { setEnabled(app.fullInfo.packageName, it) },
                    )
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppInfo, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current

    var icon by remember { mutableStateOf<Painter>(ColorPainter(Color.Transparent)) }

    LaunchedEffect(app) {
        AppInfoCache.getAppIcon(context.packageManager, app.fullInfo.packageName)
            ?.let { icon = DrawablePainter(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Image(
            modifier = Modifier
                .size(54.dp),
            painter = icon,
            contentDescription = "${app.label} icon",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
                .padding(horizontal = 8.dp)
        ) {
            Text(text = app.label)
            Text(
                modifier = Modifier.alpha(0.6f),
                text = app.fullInfo.packageName,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Preview(backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
fun AppRowPreview() {
    AppRow(
        app = AppInfo(
            label = "App name",
            fullInfo = ApplicationInfo(),
        ),
        enabled = true,
        onEnabledChange = {},
    )
}
