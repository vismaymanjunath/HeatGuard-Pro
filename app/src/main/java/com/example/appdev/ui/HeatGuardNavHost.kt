package com.example.appdev.ui

import androidx.annotation.DrawableRes
import com.example.appdev.ui.assistant.ScreenContext
import com.example.appdev.ui.assistant.AssistantRoute
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import com.example.appdev.ui.demo.DemoRoute
import com.example.appdev.HeatGuardApp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.appdev.R
import com.example.appdev.ui.analytics.AnalyticsRoute
import com.example.appdev.ui.coach.CoachRoute
import com.example.appdev.ui.dashboard.DashboardRoute
import com.example.appdev.ui.history.HistoryRoute
import com.example.appdev.ui.history.IncidentDetailRoute
import com.example.appdev.ui.history.IncidentDetailViewModel
import com.example.appdev.ui.insights.InsightsRoute
import com.example.appdev.ui.settings.SettingsRoute
import com.example.appdev.ui.theme.HeatColors

/**
 * Top-level sections. All six sections are present, so the bar never shows a tab without working functionality.
 */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, R.drawable.ic_dashboard),
    ANALYTICS("analytics", R.string.nav_live, R.drawable.ic_chart),
    COACH("coach", R.string.nav_coach, R.drawable.ic_coach),
    HISTORY("history", R.string.nav_history, R.drawable.ic_history),
    INSIGHTS("insights", R.string.nav_insights, R.drawable.ic_insights),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_tune),
}

private const val INCIDENT_ROUTE = "incident"
private const val DEMO_ROUTE = "demo"

@Composable
fun HeatGuardNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val container = (LocalContext.current.applicationContext as HeatGuardApp).container
    val demoEnabled by container.demoController.enabled.collectAsStateWithLifecycle()

    val route = currentDestination?.route
    val onAssistant = route == ScreenContext.ASSISTANT_ROUTE
    // The screen the user was on when they opened the assistant, so answers and suggestions fit it.
    var assistantOrigin by rememberSaveable { mutableStateOf<String?>(null) }
    val openAssistant = {
        assistantOrigin = route
        navController.navigate(ScreenContext.ASSISTANT_ROUTE) { launchSingleTop = true }
    }

    Scaffold(
        containerColor = HeatColors.Background,
        bottomBar = {
            // The chat is full screen so the keyboard and input bar have the whole height.
            if (!onAssistant) NavigationBar(containerColor = HeatColors.Surface, tonalElevation = 0.dp) {
                TopLevelDestination.entries.forEach { destination ->
                    // The incident detail screen belongs to the History tab.
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route ||
                            (destination == TopLevelDestination.HISTORY && it.route?.startsWith(INCIDENT_ROUTE) == true) ||
                            (destination == TopLevelDestination.SETTINGS && it.route == DEMO_ROUTE)
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = {
                            // Six tabs share the bar, so labels are kept compact and never wrap.
                            Text(
                                stringResource(destination.label),
                                fontSize = 10.sp,
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = HeatColors.Accent,
                            selectedTextColor = HeatColors.TextPrimary,
                            indicatorColor = HeatColors.SurfaceHighest,
                            unselectedIconColor = HeatColors.TextSecondary,
                            unselectedTextColor = HeatColors.TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
        if (demoEnabled) {
            DemoBanner(
                onOpenControls = { navController.navigate(DEMO_ROUTE) { launchSingleTop = true } },
                onExit = { container.demoMode.setEnabled(false) },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardRoute(onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) })
            }
            composable(TopLevelDestination.ANALYTICS.route) {
                AnalyticsRoute()
            }
            composable(TopLevelDestination.INSIGHTS.route) {
                InsightsRoute()
            }
            composable(TopLevelDestination.COACH.route) {
                CoachRoute()
            }
            composable(TopLevelDestination.HISTORY.route) {
                HistoryRoute(onOpenIncident = { id -> navController.navigate("$INCIDENT_ROUTE/$id") })
            }
            composable(
                route = "$INCIDENT_ROUTE/{${IncidentDetailViewModel.ARG_ID}}",
                arguments = listOf(navArgument(IncidentDetailViewModel.ARG_ID) { type = NavType.LongType }),
            ) {
                IncidentDetailRoute(onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsRoute(
                    onOpenDemo = { navController.navigate(DEMO_ROUTE) { launchSingleTop = true } },
                    onOpenAssistant = openAssistant,
                )
            }
            composable(DEMO_ROUTE) {
                DemoRoute(onBack = { navController.popBackStack() })
            }
            composable(ScreenContext.ASSISTANT_ROUTE) {
                AssistantRoute(originRoute = assistantOrigin, onBack = { navController.popBackStack() })
            }
        }
        // One assistant, reachable from every screen except the chat itself and the demo controls.
        if (!onAssistant && route != DEMO_ROUTE) {
            AskAiButton(
                onClick = openAssistant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
            )
        }
        }
        }
    }
}

/** Always visible while Demo Mode is on, so simulated data can never be mistaken for real data. */
@Composable
private fun DemoBanner(onOpenControls: () -> Unit, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeatColors.Accent)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.demo_banner),
            style = MaterialTheme.typography.labelLarge,
            color = HeatColors.OnAccent,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenControls) {
            Text(stringResource(R.string.demo_banner_controls), color = HeatColors.OnAccent)
        }
        TextButton(onClick = onExit) {
            Text(stringResource(R.string.demo_banner_exit), color = HeatColors.OnAccent)
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AskAiButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = HeatColors.Accent,
        contentColor = HeatColors.OnAccent,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = stringResource(R.string.ai_fab))
    }
}
