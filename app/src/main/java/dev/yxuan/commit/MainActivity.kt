package dev.yxuan.commit

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.yxuan.commit.domain.RecapKind
import dev.yxuan.commit.notify.Notifications
import dev.yxuan.commit.ui.CommitmentRoute
import dev.yxuan.commit.ui.CommitmentsRoute
import dev.yxuan.commit.ui.HistoryRoute
import dev.yxuan.commit.ui.SessionDetailRoute
import dev.yxuan.commit.ui.LiveWorkoutRoute
import dev.yxuan.commit.ui.RecapRoute
import dev.yxuan.commit.ui.RoutineEditorRoute
import dev.yxuan.commit.ui.SettingsRoute
import dev.yxuan.commit.ui.TodoEditorRoute
import dev.yxuan.commit.ui.commitments.CommitmentsScreen
import dev.yxuan.commit.ui.history.HistoryScreen
import dev.yxuan.commit.ui.history.SessionDetailScreen
import dev.yxuan.commit.ui.recap.RecapScreen
import dev.yxuan.commit.ui.settings.AccountSection
import dev.yxuan.commit.ui.settings.SettingsScreen
import dev.yxuan.commit.ui.theme.CommitTheme
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
            CommitTheme {
                CommitNavHost(pendingRoute = pendingRoute, onRouteOpened = { pendingRoute = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.deepLink()?.let { pendingRoute = it }
    }

    /** Maps intent extras set by the widget and notifications to a navigation route. */
    private fun Intent.deepLink(): Any? {
        getStringExtra(EXTRA_TODO_ID)?.let { return TodoEditorRoute(todoId = it) }
        if (getBooleanExtra(EXTRA_OPEN_WORKOUT, false)) return LiveWorkoutRoute
        getStringExtra(EXTRA_OPEN_RECAP)?.let { kind ->
            if (RecapKind.entries.any { it.name == kind }) return RecapRoute(kind)
        }
        return null
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* banner reflects the result */ }

    /** Ask for POST_NOTIFICATIONS on first launch; afterwards the commitments screen shows a banner. */
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
        const val EXTRA_OPEN_RECAP = "open_recap"
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

    NavHost(navController = nav, startDestination = CommitmentsRoute) {
        composable<CommitmentsRoute> {
            CommitmentsScreen(
                onOpen = { nav.navigate(CommitmentRoute(it)) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onOpenHistory = { nav.navigate(HistoryRoute) },
            )
        }
        composable<CommitmentRoute> {
            CommitmentDetailScreen(
                onBack = { nav.popBackStack() },
                onEditTodo = { nav.navigate(TodoEditorRoute(todoId = it)) },
                onAddTodo = { nav.navigate(TodoEditorRoute(commitmentId = it)) },
                onOpenRoutine = { nav.navigate(RoutineEditorRoute(it)) },
                onOpenLive = { nav.navigate(LiveWorkoutRoute) { launchSingleTop = true } },
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
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() }, accountSection = { AccountSection() })
        }
        composable<RecapRoute> {
            RecapScreen(onBack = { nav.popBackStack() })
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = { nav.popBackStack() }, onOpenSession = { nav.navigate(SessionDetailRoute(it)) })
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(onBack = { nav.popBackStack() })
        }
    }
}
