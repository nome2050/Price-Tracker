package com.pricetracker.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.pricetracker.app.R
import com.pricetracker.app.ui.theme.PT
import com.pricetracker.app.ui.theme.PriceTrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var openItem by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openItem = intent.getLongExtra("itemId", -1L)
        setContent {
            PriceTrackerTheme {
                val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                App(vm, openItem) { openItem = -1L }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openItem = intent.getLongExtra("itemId", -1L)
    }
}

@Composable
private fun App(vm: MainViewModel, startItem: Long, consumed: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(startItem) { if (startItem > 0) { nav.navigate("detail/$startItem"); consumed() } }
    val tabs = listOf(
        Triple("home", R.string.nav_home, Icons.Filled.Home),
        Triple("browser", R.string.nav_browser, Icons.Filled.Search),
        Triple("settings", R.string.nav_settings, Icons.Filled.Settings),
    )
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Black,
        bottomBar = {
            if (route in tabs.map { it.first }) NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Black) {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r, label = { Text(stringResource(label)) }, icon = { Icon(icon, null) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = PT.Blue, selectedTextColor = PT.Blue),
                        onClick = { nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, "home", Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("home") { HomeScreen(vm, { nav.navigate("detail/$it") }, { nav.navigate("browser") }, { nav.navigate("settings") }) }
            composable("browser") { BrowserScreen(vm) { nav.navigate("detail/$it") } }
            composable("settings") { SettingsScreen() }
            composable("detail/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                DetailScreen(it.arguments!!.getLong("id"), vm) { nav.popBackStack() }
            }
        }
    }
}
