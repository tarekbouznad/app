package com.weeklyspend.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.weeklyspend.ui.screens.AddScreen
import com.weeklyspend.ui.screens.HomeScreen
import com.weeklyspend.ui.screens.InboxScreen
import com.weeklyspend.ui.screens.MerchantRulesScreen
import com.weeklyspend.ui.screens.ParserRulesScreen
import com.weeklyspend.ui.screens.SettingsScreen
import com.weeklyspend.ui.screens.WeekScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            AppTheme { App(vm, pendingRoute.value) { pendingRoute.value = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

val Green = Color(0xFF43A047)
val Amber = Color(0xFFFFB300)
val Red = Color(0xFFE53935)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun App(vm: MainViewModel, pendingRoute: String?, consumeRoute: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val review by vm.review.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDay() }
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            consumeRoute()
        }
    }

    val tabs = listOf(
        Tab("home", "Home", Icons.Filled.Home),
        Tab("week", "Week", Icons.Filled.DateRange),
        Tab("inbox", "Inbox", Icons.Filled.List),
        Tab("settings", "Settings", Icons.Filled.Settings),
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == "inbox" && review.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${review.size}") } }) { Icon(tab.icon, null) }
                                } else {
                                    Icon(tab.icon, null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, snackbar, onAdd = { nav.navigate("add") }) }
            composable("week") { WeekScreen(vm) }
            composable("inbox") { InboxScreen(vm, onAddFromReview = { amt, note -> nav.navigate("add?amount=$amt&note=${android.net.Uri.encode(note)}") }) }
            composable("settings") {
                SettingsScreen(vm, snackbar, onParserRules = { nav.navigate("parser_rules") }, onMerchantRules = { nav.navigate("merchant_rules") })
            }
            composable(
                "add?amount={amount}&note={note}",
                arguments = listOf(
                    navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                    navArgument("note") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                AddScreen(
                    vm,
                    initialAmount = entry.arguments?.getString("amount").orEmpty(),
                    initialNote = entry.arguments?.getString("note").orEmpty(),
                    onDone = { nav.popBackStack() },
                )
            }
            composable("parser_rules") { ParserRulesScreen(vm, onBack = { nav.popBackStack() }) }
            composable("merchant_rules") { MerchantRulesScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
