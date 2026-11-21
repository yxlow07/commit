package dev.yxuan.commit.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.sync.SyncState
import kotlinx.coroutines.launch

/** Settings → Account: Google sign-in and sync status (M7). */
@Composable
fun AccountSection() {
    val context = LocalContext.current
    val sync = context.appContainer.syncManager
    val state by sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    SectionTitle("Account & sync")
    when {
        !state.configured -> Text(
            "Sync isn't set up in this build. Add the Supabase and Google keys to local.properties (see SYNC_SETUP.md); until then everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        !state.signedIn -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Sign in to sync with the web app. Everything keeps working offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp),
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        sync.signInWithGoogle(context.findActivity() ?: context)
                            .onFailure { message = signInError(it) }
                        busy = false
                    }
                },
            ) { Text("Sign in with Google") }
        }

        else -> {
            ListItem(
                headlineContent = { Text(state.email ?: "Signed in") },
                supportingContent = { Text(statusLine(state)) },
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { scope.launch { sync.signOut() } }) { Text("Sign out") }
            }
            Text(
                "Signing out stops syncing; your data stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    message?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** Plain-language sign-in errors; the common one is a device with no Google account. */
private fun signInError(e: Throwable): String = when (e) {
    is NoCredentialException ->
        "No Google account on this device. Add one in Android Settings → Passwords & accounts, then try again."
    is GetCredentialCancellationException -> "Sign-in cancelled."
    else -> "Sign-in failed: ${e.message ?: e::class.simpleName}"
}

private fun statusLine(s: SyncState): String = when {
    s.syncing -> "Syncing…"
    s.error != null -> "Couldn't sync: ${s.error}" + pendingSuffix(s)
    s.lastSyncedAt != null -> "Synced " + syncedAgo(s.lastSyncedAt) + pendingSuffix(s)
    else -> "Waiting for the first sync" + pendingSuffix(s)
}

/** "just now" under a minute, then "5 minutes ago", "2 hours ago"… */
private fun syncedAgo(at: Long): String {
    val now = System.currentTimeMillis()
    return if (now - at < DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

private fun pendingSuffix(s: SyncState): String =
    if (s.pending > 0) " · ${s.pending} change${if (s.pending == 1) "" else "s"} to upload" else ""

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
