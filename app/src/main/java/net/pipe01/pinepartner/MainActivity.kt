package net.pipe01.pinepartner

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import net.pipe01.pinepartner.data.AppDatabase
import net.pipe01.pinepartner.scripting.BuiltInPlugins
import net.pipe01.pinepartner.service.BackgroundService
import net.pipe01.pinepartner.ui.theme.PinePartnerTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.create(applicationContext)

        BuiltInPlugins.init(assets)

        setContent {
            val navController = rememberNavController()

            PinePartnerTheme {
                Scaffold(
                    bottomBar = {
                        BottomBar(navController = navController)
                    }
                ) { padding ->
                    PermissionsFrame {
                        NavFrame(
                            modifier = Modifier.padding(padding),
                            navController = navController,
                            db = db,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(this, BackgroundService::class.java)
        startForegroundService(intent)
    }
}
