package dev.yxuan.commit.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.text.format.DateUtils
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.sync.SyncState
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.SecondaryButton
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import kotlinx.coroutines.launch

/** Settings → Account and sync: Google sign-in and sync status (M7). */
@Composable
fun AccountSection() {
    val context = LocalContext.current
    val sync = context.appContainer.syncManager
    val state by sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val c = Commit.colors

    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionHeading("Account")
        when {
            !state.configured -> Text(
                "Sync isn't set up in this build (see SYNC_SETUP.md).",
                style = Commit.type.meta,
                color = c.textStrong,
            )

            !state.signedIn -> {
                Text("Sync with the web app.", style = Commit.type.meta, color = c.textStrong)
                PrimaryButton(
                    "Sign in with Google",
                    enabled = !busy,
                    modifier = Modifier.padding(top = Space.xs),
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            sync.signInWithGoogle(context.findActivity() ?: context)
                                .onFailure { message = signInError(it) }
                            busy = false
                        }
                    },
                )
            }

            else -> {
                Column(Modifier.heightIn(min = Space.rowApp), verticalArrangement = Arrangement.Center) {
                    Text(state.email ?: "Signed in", style = Commit.type.body, color = c.ink)
                    Text(statusLine(state), style = Commit.type.meta, color = if (state.error != null) c.danger else c.muted)
                }
                SecondaryButton("Sign out", onClick = { scope.launch { sync.signOut() } })
            }
        }
        message?.let { Text(it, style = Commit.type.meta, color = c.danger) }
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
    s.syncing -> "Syncing"
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
    if (s.pending > 0) ", ${s.pending} change${if (s.pending == 1) "" else "s"} to upload" else ""

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
