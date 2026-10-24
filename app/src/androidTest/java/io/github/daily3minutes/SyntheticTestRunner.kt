// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes

import android.app.Application
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import io.github.daily3minutes.data.DailyDatabase
import io.github.daily3minutes.data.DailyRepository
import io.github.daily3minutes.widget.DailyWidget
import io.github.daily3minutes.reminder.ReminderAlarmProbe

/** Instrumentation never opens the user's production records database. */
class SyntheticTestRunner : AndroidJUnitRunner() {
    private var alarmProbe = false
    override fun onCreate(arguments: Bundle) {
        alarmProbe = arguments.getString("alarmProbe") == "true"
        super.onCreate(arguments)
    }

    override fun onStart() {
        // Test windows keep the display awake without changing the user's screen timeout or lock settings.
        (targetContext.applicationContext as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 27) activity.setTurnScreenOn(true)
            }
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        val granted = Build.VERSION.SDK_INT < 33 || targetContext.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        sendStatus(0, Bundle().apply { putString("daily3_initial_notification_permission", if (granted) "granted" else "denied") })
        sendStatus(0, Bundle().apply { putString("daily3_initial_microphone_permission", if (
            targetContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "granted" else "denied") })
        if (alarmProbe) ReminderAlarmProbe.run(this) else super.onStart()
    }

    override fun newApplication(loader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(loader, SyntheticTestApplication::class.java.name, context)
}

class SyntheticTestApplication : DailyApp() {
    var syntheticDictation: io.github.daily3minutes.audio.DictationProvider? = null
    override val useDictationService = true
    override val dictation = io.github.daily3minutes.audio.DictationProvider { language, result, error ->
        requireNotNull(syntheticDictation) { "Live speech is disabled in synthetic instrumentation" }.start(language, result, error)
    }
    override val voices by lazy { io.github.daily3minutes.audio.VoiceLibrary(database,
        java.io.File(cacheDir, "synthetic-voice-${java.util.UUID.randomUUID()}"),
        { error("Microphone capture is disabled in synthetic instrumentation; inject a fake explicitly") }) }
    override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
        super.getSharedPreferences("instrumentation_v040_$name", mode)
    override val database by lazy { Room.inMemoryDatabaseBuilder(this, DailyDatabase::class.java).build() }
    override val repository by lazy {
        DailyRepository(database) {
            DailyWidget.refresh(this)
        }
    }
}
