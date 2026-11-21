package dev.yxuan.commit.data.repo

import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.ThemeEntity
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.BuiltinThemes
import dev.yxuan.commit.domain.theme.ThemeParseResult
import dev.yxuan.commit.domain.theme.ThemeParser
import dev.yxuan.commit.domain.theme.ThemeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.util.UUID

class ThemeRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val settingsRepository: SettingsRepository,
    private val changeListener: DataChangeListener,
) {
    private val themeDao = db.themeDao()

    fun observeThemes(): Flow<List<ThemeEntity>> = themeDao.observeAll()

    fun observeActive(): Flow<ActiveTheme> = combine(
        settingsRepository.observe(),
        observeThemes(),
    ) { settings, themes ->
        val activeId = settings.activeThemeId
        BuiltinThemes.resolve(activeId) { id ->
            themes.firstOrNull { it.id == id }?.let { ThemeParser.parseOrNull(it.json) }
        }
    }

    suspend fun import(json: String): Result<ThemeSpec> {
        val result = ThemeParser.parse(json)
        return when (result) {
            is ThemeParseResult.Invalid -> Result.failure(IllegalArgumentException(result.errors.joinToString("\n")))
            is ThemeParseResult.Ok -> {
                val now = clock.millis()
                val id = UUID.randomUUID().toString()
                val entity = ThemeEntity(
                    id = id,
                    updatedAt = now,
                    dirty = true,
                    name = result.spec.name,
                    json = json,
                )
                themeDao.upsert(entity)
                changeListener.onDataChanged()
                Result.success(result.spec)
            }
        }
    }

    suspend fun importFromUrl(urlString: String): Result<ThemeSpec> = withContext(Dispatchers.IO) {
        val trimmed = urlString.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Theme URL must start with https://"))
        }
        var connection: HttpURLConnection? = null
        try {
            val url = URL(trimmed)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            connection.connect()
            // HttpURLConnection never follows a redirect from https to http, so the download stays on https.
            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalArgumentException("Server returned status ${connection.responseCode}")
                )
            }
            val bytes = connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val temp = ByteArray(4096)
                var count: Int
                while (stream.read(temp).also { count = it } != -1) {
                    buffer.write(temp, 0, count)
                    if (buffer.size() > ThemeParser.MAX_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException("The file is larger than 32 KB; a theme is a few hundred bytes.")
                        )
                    }
                }
                buffer.toByteArray()
            }
            val text = bytes.toString(Charsets.UTF_8)
            import(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "Failed to download theme"))
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun select(id: String?) {
        val targetId = if (id == BuiltinThemes.DYNAMIC) null else id
        settingsRepository.update { it.copy(activeThemeId = targetId) }
    }

    suspend fun delete(id: String) {
        val now = clock.millis()
        themeDao.softDelete(id, now)
        val currentSettings = settingsRepository.get()
        if (currentSettings.activeThemeId == id) {
            settingsRepository.update { it.copy(activeThemeId = null) }
        }
        changeListener.onDataChanged()
    }
}
