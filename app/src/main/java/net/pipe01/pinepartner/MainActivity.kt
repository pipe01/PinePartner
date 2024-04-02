package net.pipe01.pinepartner

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.pages.ConnectingServicePage
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.service.ServiceHandle
import net.pipe01.pinepartner.ui.theme.PinePartnerTheme
import net.pipe01.pinepartner.utils.composables.PluginsDisabledDialog

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private val serviceHandle = ServiceHandle(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.create(applicationContext)

        BuiltInPlugins.init(assets)

        setContent {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }

            var showBottomBar by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                serviceHandle.start()

                onDispose {
                    serviceHandle.unbind()
                }
            }

            PinePartnerTheme {
                if (serviceHandle.service == null) {
                    ConnectingServicePage(crashed = serviceHandle.hasCrashed)
                } else {
                    var showPluginsDisabledDialog by remember { mutableStateOf(false) }

                    if (serviceHandle.pluginsDisabled) {
                        LaunchedEffect(Unit) {
                            val result = snackbarHostState.showSnackbar(
                                message = "Plugins have been disabled",
                                duration = SnackbarDuration.Long,
                                actionLabel = "Why?",
                            )
                            when (result) {
                                SnackbarResult.ActionPerformed -> {
                                    showPluginsDisabledDialog = true
                                }
                                SnackbarResult.Dismissed -> {
                                }
                            }
                        }
                    }

                    if (showPluginsDisabledDialog) {
                        PluginsDisabledDialog(
                            onDismissRequest = { showPluginsDisabledDialog = false }
                        )
                    }

                    Scaffold(
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState)
                        },
                        bottomBar = {
                            if (showBottomBar) {
                                BottomBar(navController = navController)
                            }
                        }
                    ) { padding ->
                        PermissionsFrame(
                            onGotAllPermissions = { showBottomBar = true },
                        ) {
                            NavFrame(
                                modifier = Modifier.padding(padding),
                                navController = navController,
                                backgroundService = serviceHandle.service!!,
                                onShowBottomBar = { showBottomBar = it },
                                db = db,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        serviceHandle.start()
    }
}
