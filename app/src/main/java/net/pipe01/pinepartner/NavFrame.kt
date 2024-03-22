package net.pipe01.pinepartner

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.pages.devices.AddDevicePage
import net.pipe01.pinepartner.pages.devices.DevicePage
import net.pipe01.pinepartner.pages.devices.DevicesPage
import net.pipe01.pinepartner.pages.plugins.ImportPluginPage
import net.pipe01.pinepartner.pages.plugins.PluginPage
import net.pipe01.pinepartner.pages.plugins.PluginsPage
import net.pipe01.pinepartner.pages.settings.NotificationSettingsPage
import net.pipe01.pinepartner.pages.settings.SettingsPage

object Route {
    const val DEVICES = "devices"
    const val PLUGINS = "scripting"
    const val SETTINGS = "settings"

    const val DEVICES_HOME = "devices/home"
    const val DEVICES_ADD = "devices/add"

    const val PLUGINS_HOME = "plugins/home"
    const val PLUGINS_IMPORT = "plugins/import"

    const val SETTINGS_HOME = "settings/home"
    const val SETTINGS_NOTIFS = "settings/notifications"

    val TOP_ORDER = arrayOf(
        DEVICES,
        PLUGINS,
        SETTINGS,
    )
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.getRouteTransitionDirection(): AnimatedContentTransitionScope.SlideDirection? {
    val fromTopLevel = initialState.destination.hierarchy.filter { it.route != null }.last()
    val toTopLevel = targetState.destination.hierarchy.filter { it.route != null }.last()

    val orderDelta = Route.TOP_ORDER.indexOf(toTopLevel.route) - Route.TOP_ORDER.indexOf(fromTopLevel.route)

    return if (orderDelta == 0)
        null
    else if (orderDelta > 0)
        AnimatedContentTransitionScope.SlideDirection.Left
    else
        AnimatedContentTransitionScope.SlideDirection.Right
}

@Composable
fun NavFrame(navController: NavHostController, db: AppDatabase, modifier: Modifier = Modifier) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Route.DEVICES,
        enterTransition = {
            val dir = getRouteTransitionDirection()

            if (dir == null) {
                fadeIn(animationSpec = tween())
            } else {
                slideIntoContainer(
                    towards = dir,
                    animationSpec = tween()
                )
            }
        },
        exitTransition = {
            val dir = getRouteTransitionDirection()

            if (dir == null) {
                fadeOut(animationSpec = tween())
            } else {
                slideOutOfContainer(
                    towards = dir,
                    animationSpec = tween()
                )
            }
        },
    ) {
        navigation(startDestination = Route.SETTINGS_HOME, route = Route.SETTINGS) {
            composable(Route.SETTINGS_HOME) {
                SettingsPage(
                    onNotificationSettings = { navController.navigate(Route.SETTINGS_NOTIFS) },
                )
            }
            composable(Route.SETTINGS_NOTIFS) {
                NotificationSettingsPage(
                    db = db,
                )
            }
        }
        navigation(startDestination = Route.PLUGINS_HOME, route = Route.PLUGINS) {
            composable(Route.PLUGINS_HOME) {
                PluginsPage(
                    db = db,
                    onPluginClicked = { navController.navigate("${Route.PLUGINS}/${it.id}") },
                    onImportPlugin = { navController.navigate(Route.PLUGINS_IMPORT) },
                )
            }
            composable(Route.PLUGINS_IMPORT) {
                ImportPluginPage(
                    pluginDao = db.pluginDao(),
                    onDone = { navController.navigate(Route.PLUGINS) },
                )
            }
            composable("${Route.PLUGINS}/{id}") {
                val id = it.arguments?.getString("id")
                PluginPage(
                    pluginDao = db.pluginDao(),
                    id = id!!,
                    onRemoved = { navController.navigate(Route.PLUGINS) }
                )
            }
        }
        navigation(startDestination = Route.DEVICES_HOME, route = Route.DEVICES) {
            composable(Route.DEVICES_HOME) {
                DevicesPage(
                    db = db,
                    onAddDevice = { navController.navigate(Route.DEVICES_ADD) },
                    onDeviceClick = { address -> navController.navigate("${Route.DEVICES}/$address") },
                )
            }
            composable(Route.DEVICES_ADD) {
                AddDevicePage(
                    db = db,
                    onDone = {
                        navController.navigate(
                            route = Route.DEVICES,
                        ) {
                            popUpTo(Route.DEVICES) { inclusive = true }
                        }
                    }
                )
            }
            composable("${Route.DEVICES}/{address}") {
                val address = it.arguments?.getString("address")
                DevicePage(
                    db = db,
                    deviceAddress = address!!,
                )
            }
        }
    }
}

@Composable
fun BottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        @Composable
        fun item(route: String, targetRoute: String, name: String, icon: ImageVector) {
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                onClick = {
                    navController.navigate(targetRoute) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                    }
                },
                icon = { Icon(icon, contentDescription = name) },
                label = { Text(text = name) }
            )
        }

        Route.TOP_ORDER.forEach {
            when (it) {
                Route.DEVICES -> item(it, it, "Devices", Icons.Filled.Watch)
                Route.PLUGINS -> item(it, it, "Plugins", Icons.Filled.Extension)
                Route.SETTINGS -> item(it, it, "Settings", Icons.Filled.Settings)
            }
        }
    }
}

@Preview
@Composable
fun BottomBarPreview() {
    BottomBar(rememberNavController())
}