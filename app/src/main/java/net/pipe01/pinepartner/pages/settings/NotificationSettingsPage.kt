package net.pipe01.pinepartner.pages.settings

import android.R
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.pipe01.pinepartner.data.AppDatabase

data class InstalledApp(val name: String, val packageName: String, val icon: Drawable)

@SuppressLint("QueryPermissionsNeeded")
@Composable
fun NotificationSettingsPage(db: AppDatabase) {
    val context = LocalContext.current

    val installedApps = remember { mutableStateListOf<InstalledApp>() }
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
//                    .take(50)
                    .map {
                        InstalledApp(
                            it.loadLabel(context.packageManager).toString(),
                            it.packageName,
                            it.loadIcon(context.packageManager)
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .sortedByDescending { enabledPackages.contains(it.packageName) }
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
                    installedApps.filter { it.name.contains(filter, ignoreCase = true) }
                }
            }

            LazyColumn {
                items(filteredApps.size) { index ->
                    val app = filteredApps[index]

                    AppRow(
                        app = app,
                        enabled = enabledPackages.contains(app.packageName),
                        onEnabledChange = { setEnabled(app.packageName, it) },
                    )
                }
            }
        }
    }
}

@Composable
fun AppRow(app: InstalledApp, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val icon = rememberDrawablePainter(drawable = app.icon)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Image(
            modifier = Modifier
                .size(54.dp),
            painter = icon,
            contentDescription = "${app.name} icon",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
                .padding(horizontal = 8.dp)
        ) {
            Text(text = app.name)
            Text(
                modifier = Modifier.alpha(0.6f),
                text = app.packageName,
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
        app = InstalledApp(
            "App name",
            "com.example.app",
            ContextCompat.getDrawable(LocalContext.current, R.drawable.sym_def_app_icon)!!
        ),
        enabled = true,
        onEnabledChange = {},
    )
}
