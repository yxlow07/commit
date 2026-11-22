package dev.yxuan.commit

import dev.yxuan.commit.ui.settings.ArchivedScreen
import dev.yxuan.commit.ui.ArchivedRoute
import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.yxuan.commit.domain.RecapKind
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.notify.Notifications
import dev.yxuan.commit.ui.CommitmentRoute
import dev.yxuan.commit.ui.HistoryRoute
import dev.yxuan.commit.ui.LiveWorkoutRoute
import dev.yxuan.commit.ui.RecapRoute
import dev.yxuan.commit.ui.RoutineEditorRoute
import dev.yxuan.commit.ui.SessionDetailRoute
import dev.yxuan.commit.ui.SettingsRoute
import dev.yxuan.commit.ui.ThemeRoute
import dev.yxuan.commit.ui.TodayRoute
import dev.yxuan.commit.ui.TodoEditorRoute
import dev.yxuan.commit.ui.components.BottomNav
import dev.yxuan.commit.ui.components.NavTab
import dev.yxuan.commit.ui.history.HistoryScreen
import dev.yxuan.commit.ui.history.SessionDetailScreen
import dev.yxuan.commit.ui.recap.RecapScreen
import dev.yxuan.commit.ui.settings.AccountSection
import dev.yxuan.commit.ui.settings.SettingsScreen
import dev.yxuan.commit.ui.settings.ThemeScreen
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.CommitTheme
import dev.yxuan.commit.ui.theme.isDark
import dev.yxuan.commit.ui.today.TodayScreen
import dev.yxuan.commit.ui.todos.CommitmentDetailScreen
import dev.yxuan.commit.ui.todos.TodoEditorScreen
import dev.yxuan.commit.ui.workout.LiveWorkoutScreen
import dev.yxuan.commit.ui.workout.RoutineEditorScreen

class MainActivity : ComponentActivity() {

    /** Screen to open from a widget row or notification tap. Consumed once navigated. */
    private var pendingRoute by mutableStateOf<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            pendingRoute = intent.deepLink()
            requestNotificationsOnce()
        }
        setContent {
            val activeTheme by appContainer.themeRepository.observeActive().collectAsState(ActiveTheme.Default)
            CommitTheme(active = activeTheme) {
                // System bar icons follow the theme in use (a forced Dark theme on a light system, too).
                val dark = activeTheme.isDark()
                LaunchedEffect(dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    )
                }
                Box(Modifier.fillMaxSize().background(Commit.colors.background)) {
                    CommitNavHost(pendingRoute = pendingRoute, onRouteOpened = { pendingRoute = null })
                }
            }
        }
    }

    /** Sync whenever the app comes to the front, so a phone that was off catches up right away. */
    override fun onStart() {
        super.onStart()
        appContainer.syncManager.requestSync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.deepLink()?.let { pendingRoute = it }
    }

    /** Maps intent extras set by the widget and notifications to a navigation route. */
    private fun Intent.deepLink(): Any? {
        getStringExtra(EXTRA_TODO_ID)?.let { return TodoEditorRoute(todoId = it) }
        if (getBooleanExtra(EXTRA_NEW_COMMITMENT, false)) return TodayRoute(newCommitment = true)
        if (getBooleanExtra(EXTRA_OPEN_WORKOUT, false)) return LiveWorkoutRoute
        getStringExtra(EXTRA_OPEN_SESSION)?.let { return SessionDetailRoute(it) }
        // Workouts live on Today now.
        if (getBooleanExtra(EXTRA_OPEN_WORKOUTS, false)) return TodayRoute()
        getStringExtra(EXTRA_OPEN_RECAP)?.let { kind ->
            if (RecapKind.entries.any { it.name == kind }) return RecapRoute(kind)
        }
        return null
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* banner reflects the result */ }

    /** Ask for POST_NOTIFICATIONS on first launch; afterwards Today and Settings show a notice. */
    private fun requestNotificationsOnce() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (Notifications.canPost(this) || prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        /** Matches the widget's `todo_id` action parameter, which Glance passes as an intent extra. */
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_OPEN_WORKOUT = "open_workout"
        /** A workout session's sets (History), e.g. from the widget's finished-workout panel. */
        const val EXTRA_OPEN_SESSION = "open_session"
        const val EXTRA_OPEN_WORKOUTS = "open_workouts"
        const val EXTRA_OPEN_RECAP = "open_recap"
        const val EXTRA_NEW_COMMITMENT = "new_commitment"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}

@Composable
private fun CommitNavHost(pendingRoute: Any?, onRouteOpened: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteOpened()
        }
    }

    // Bottom-nav tabs keep their own back stacks; Today is the root.
    val goTab: (NavTab) -> Unit = { tab ->
        val route: Any = when (tab) {
            NavTab.Today -> TodayRoute()
            NavTab.History -> HistoryRoute
            NavTab.Settings -> SettingsRoute
        }
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(navController = nav, startDestination = TodayRoute()) {
        composable<TodayRoute> { entry ->
            val newCommitment = entry.toRoute<TodayRoute>().newCommitment
            TabShell(NavTab.Today, goTab) { m ->
                TodayScreen(
                    onOpenCommitment = { nav.navigate(CommitmentRoute(it)) },
                    onOpenRoutine = { nav.navigate(RoutineEditorRoute(it)) },
                    onOpenLive = { nav.navigate(LiveWorkoutRoute) { launchSingleTop = true } },
                    startCreating = newCommitment,
                    modifier = m,
                )
            }
        }
        composable<HistoryRoute> {
            TabShell(NavTab.History, goTab) { m ->
                HistoryScreen(onOpenSession = { nav.navigate(SessionDetailRoute(it)) }, modifier = m)
            }
        }
        composable<SettingsRoute> {
            TabShell(NavTab.Settings, goTab) { m ->
                SettingsScreen(onOpenTheme = { nav.navigate(ThemeRoute) }, onOpenArchived = { nav.navigate(ArchivedRoute) }, accountSection = { AccountSection() }, modifier = m)
            }
        }
        composable<ThemeRoute> {
            TabShell(NavTab.Settings, goTab) { m -> ThemeScreen(onBack = { nav.popBackStack() }, modifier = m) }
        }
        composable<ArchivedRoute> {
            TabShell(NavTab.Settings, goTab) { m -> ArchivedScreen(onBack = { nav.popBackStack() }, modifier = m) }
        }
        composable<RecapRoute> {
            TabShell(NavTab.Today, goTab) { m -> RecapScreen(onBack = { nav.popBackStack() }, modifier = m) }
        }
        // Pushed screens, without the bottom nav.
        composable<CommitmentRoute> {
            CommitmentDetailScreen(
                onBack = { nav.popBackStack() },
                onEditTodo = { nav.navigate(TodoEditorRoute(todoId = it)) },
                onOpenWorkouts = { nav.popBackStack() },
            )
        }
        composable<RoutineEditorRoute> {
            RoutineEditorScreen(onBack = { nav.popBackStack() })
        }
        composable<LiveWorkoutRoute> {
            LiveWorkoutScreen(onBack = { nav.popBackStack() })
        }
        composable<TodoEditorRoute> {
            TodoEditorScreen(onDone = { nav.popBackStack() })
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** Top-level screen with the bottom nav; content gets the status-bar inset. */
@Composable
private fun TabShell(tab: NavTab, onTab: (NavTab) -> Unit, content: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).statusBarsPadding()) { content(Modifier) }
        BottomNav(selected = tab, onSelect = onTab)
    }
}
