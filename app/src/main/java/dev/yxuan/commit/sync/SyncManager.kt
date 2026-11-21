package dev.yxuan.commit.sync

import android.content.Context
import android.util.Log
import dev.yxuan.commit.BuildConfig
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.repo.SettingsRepository
import dev.yxuan.commit.domain.RetentionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Sync keys from `local.properties` (see SYNC_SETUP.md). Any key missing = sync is off. */
object SyncConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val isConfigured: Boolean
        get() = listOf(supabaseUrl, supabaseAnonKey, googleWebClientId).all { it.isNotBlank() }
}

/** What the Settings → Account section shows. */
data class SyncState(
    val configured: Boolean,
    val signedIn: Boolean = false,
    val email: String? = null,
    val syncing: Boolean = false,
    /** Phone time of the last successful sync. */
    val lastSyncedAt: Long? = null,
    val pending: Int = 0,
    val error: String? = null,
)

/**
 * Sync with Supabase (M7, hand-written; see [SyncEngine]). Only does anything when
 * [SyncConfig.isConfigured] and the user is signed in with Google; otherwise the app is fully local.
 *
 * Runs after local writes, every 15 minutes, and on FCM pings (all through [SyncWorker]), plus when
 * the app starts. After a sync that pulled rows, [onRemoteChange] redraws the widget and reschedules
 * alarms (PRD sync rule 7).
 */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val onRemoteChange: suspend () -> Unit,
) {
    val isConfigured: Boolean = SyncConfig.isConfigured

    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SyncState(configured = isConfigured, lastSyncedAt = prefs.getLong(KEY_LAST_SYNC, 0).takeIf { it > 0 }))
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val supabase: SupabaseClient? by lazy {
        if (!isConfigured) null else createSupabaseClient(SyncConfig.supabaseUrl, SyncConfig.supabaseAnonKey) {
            install(Auth)
            install(Postgrest)
        }
    }

    private val engine: SyncEngine? by lazy {
        supabase?.let { SyncEngine(db, SupabaseRemote(it), PrefsCursors(prefs)) }
    }

    private val mutex = Mutex()

    /** Follows the sign-in state and syncs once signed in. Called once from `CommitApp`. */
    fun start() {
        val client = supabase ?: return
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        _state.update { it.copy(signedIn = true, email = user?.email) }
                        user?.id?.let { onSignedIn(it) }
                    }
                    is SessionStatus.NotAuthenticated -> _state.update { it.copy(signedIn = false, email = null) }
                    else -> Unit
                }
            }
        }
    }

    /** Google sign-in via Credential Manager → Supabase Auth (PRD: Google sign-in). */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = runCatching {
        val client = supabase ?: error("Sync is not configured")
        val token = GoogleSignIn.requestIdToken(activityContext, SyncConfig.googleWebClientId)
        client.auth.signInWith(IDToken) {
            idToken = token.idToken
            provider = Google
            nonce = token.rawNonce
        }
    }

    /** Signs out and stops syncing. Local data stays on the phone. */
    suspend fun signOut() {
        SyncWorker.cancelAll(context)
        supabase?.auth?.signOut()
    }

    /** After local writes: a background job pushes them as soon as there's network (PRD rule 1). */
    fun requestSync() {
        if (!isConfigured || !_state.value.signedIn) return
        SyncWorker.enqueueNow(context)
        // Show "N changes to upload" right away, also while offline when no sync can run.
        val engine = engine ?: return
        scope.launch { _state.update { it.copy(pending = engine.pendingCount()) } }
    }

    /** One push + pull round. Safe to call concurrently; runs one at a time. */
    suspend fun syncOnce() {
        val engine = engine ?: return
        if (supabase?.auth?.currentSessionOrNull() == null) return
        mutex.withLock {
            _state.update { it.copy(syncing = true) }
            try {
                var result = engine.sync()
                // Writes made while this round ran are pushed now rather than waiting for the next trigger.
                repeat(2) {
                    if (engine.pendingCount() == 0) return@repeat
                    val again = engine.sync()
                    result = SyncResult(result.pushed + again.pushed, result.pulled + again.pulled)
                }
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                _state.update { it.copy(syncing = false, lastSyncedAt = now, pending = engine.pendingCount(), error = null) }
                if (result.pulled > 0) onRemoteChange()
                purgeOldHistoryDaily(now)
            } catch (e: Exception) {
                _state.update { it.copy(syncing = false, pending = runCatching { engine.pendingCount() }.getOrDefault(it.pending), error = e.message ?: e::class.simpleName) }
                throw e
            }
        }
    }

    /** Stores this device's FCM token so the Edge Function can wake it after web edits. */
    /**
     * 12-month local retention (PRD NF), at most once a day, after a successful sync and inside the
     * sync lock: only rows that have reached the server are removed, and no pull is half-applied.
     */
    private suspend fun purgeOldHistoryDaily(now: Long) {
        if (now - prefs.getLong(KEY_LAST_PURGE, 0) < 24 * 60 * 60 * 1000L) return
        runCatching { RetentionManager.purgeLocalHistory(db, java.time.Clock.systemDefaultZone()) }
            .onSuccess { pruned ->
                prefs.edit().putLong(KEY_LAST_PURGE, now).apply()
                if (pruned > 0) Log.i("CommitSync", "Retention: removed $pruned local history rows older than 12 months")
            }
            .onFailure { Log.w("CommitSync", "Retention purge failed", it) }
    }

    suspend fun registerPushToken(token: String) {
        val client = supabase ?: return
        if (client.auth.currentSessionOrNull() == null) {
            prefs.edit().putString(KEY_PENDING_TOKEN, token).apply()
            return
        }
        runCatching {
            client.from("device_tokens").upsert(mapOf("token" to token, "platform" to "android"))
        }.onSuccess { prefs.edit().remove(KEY_PENDING_TOKEN).apply() }
    }

    private suspend fun onSignedIn(userId: String) {
        rekeySettings(userId)
        SyncWorker.schedulePeriodic(context)
        SyncWorker.enqueueNow(context)
        prefs.getString(KEY_PENDING_TOKEN, null)?.let { registerPushToken(it) }
        PushTokens.refresh(context, this)
    }

    /**
     * One settings row per user, shared with the web app: the local pre-sign-in row
     * ([SettingsRepository.LOCAL_ID]) becomes `settings-<user id>` and is pushed.
     */
    private suspend fun rekeySettings(userId: String) {
        val dao = db.settingsDao()
        val local = dao.getById(SettingsRepository.LOCAL_ID) ?: return
        dao.upsert(local.copy(id = SettingsRepository.idFor(userId), updatedAt = System.currentTimeMillis(), dirty = true))
        dao.deleteById(SettingsRepository.LOCAL_ID)
    }

    private companion object {
        const val KEY_PENDING_TOKEN = "pending_push_token"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_PURGE = "last_retention_purge"
    }
}

/** [SyncRemote] over Supabase PostgREST. Row-level security limits every call to the user's own rows. */
private class SupabaseRemote(private val client: SupabaseClient) : SyncRemote {
    override suspend fun upsert(table: String, rowsJson: String) {
        val rows = Json.parseToJsonElement(rowsJson).jsonArray.map { it.jsonObject }
        client.from(table).upsert(rows)
    }

    override suspend fun pull(table: String, since: Long, offset: Int, limit: Int): String =
        client.from(table).select {
            filter { gt("updated_at", since) }
            order("updated_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.data
}

private class PrefsCursors(private val prefs: android.content.SharedPreferences) : SyncCursors {
    override fun get(table: String): Long = prefs.getLong("cursor_$table", 0)
    override fun set(table: String, value: Long) {
        prefs.edit().putLong("cursor_$table", value).apply()
    }
}
