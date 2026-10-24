package io.github.daily3minutes

import android.app.Application
import io.github.daily3minutes.data.DailyDatabase
import io.github.daily3minutes.data.DailyRepository
import io.github.daily3minutes.widget.DailyWidget
import io.github.daily3minutes.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class DailyApp : Application() {
    open val dictation: io.github.daily3minutes.audio.DictationProvider by lazy { io.github.daily3minutes.audio.SystemDictation(this) }
    open val useDictationService: Boolean get() = packageManager.resolveActivity(
        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0) == null
    open val database by lazy { DailyDatabase.get(this) }
    open val sync by lazy { SyncCoordinator(this, database) }
    open val voices by lazy { io.github.daily3minutes.audio.VoiceLibrary(database,
        java.io.File(filesDir, "voice-notes"), { io.github.daily3minutes.audio.AndroidVoiceCapture(this) },
        onChanged = { DailyWidget.refresh(this) }) }
    val voiceArchives by lazy { io.github.daily3minutes.backup.VoiceArchiveManager(voices, repository, cacheDir) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Any()
    private var pendingRefresh: Job? = null
    open val repository by lazy {
        DailyRepository(database, onChanged = {
            appScope.launch { if (database.sync().active() != null) SyncCoordinator.schedule(this@DailyApp) }
            // A failed widget refresh cannot turn a committed record into a failed save.
            synchronized(refreshLock) {
                pendingRefresh?.cancel()
                pendingRefresh = appScope.launch {
                    delay(200)
                    // Detach the actual update from the cancellable debounce timer.
                    appScope.launch { runCatching { DailyWidget.refresh(this@DailyApp) } }
                    appScope.launch { runCatching { voices.pruneOrphans() } }
                }
            }
        })
    }
    override fun onCreate() {
        super.onCreate()
        io.github.daily3minutes.ui.AppLocale.initialize(this)
        DailyWidget.scheduleRefresh(this)
        appScope.launch { if (database.sync().active() != null) SyncCoordinator.schedule(this@DailyApp) }
    }
}
