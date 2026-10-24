// Copyright 2026 Daily 3 Minutes contributors. SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.content.BroadcastReceiver
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.daily3minutes.DailyApp
import io.github.daily3minutes.reminder.ReminderScheduler
import io.github.daily3minutes.widget.DailyWidget
import io.github.daily3minutes.widget.DailyWidgetReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }
    private val model: DailyViewModel by viewModels {
        DailyViewModel.Factory((application as DailyApp).repository)
    }
    private val syncBarrier: suspend (suspend () -> Boolean) -> Boolean = { model.withSyncCommit(it) }
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.refreshToday() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as DailyApp).sync.editorBarrier = syncBarrier
        enableEdgeToEdge()
        ContextCompat.registerReceiver(this, dateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (savedInstanceState == null) openIntent(intent)
        // Foreground fallback complements system broadcasts. It stops in the background
        // and retries date changes deferred while a save or SAF operation was active.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    model.checkDayBoundary()
                    delay(model.nextDayCheckDelayMillis())
                }
            }
        }
        setContent { Daily3Theme { DailyAppContent(model, onFinish = ::finish) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    private fun openIntent(intent: Intent?) {
        model.externalDestination(intent?.getStringExtra("destination"), intent?.getStringExtra("date"),
            intent?.getIntExtra("question", -1)?.takeIf { it in 0..2 })
    }

    override fun onResume() {
        super.onResume()
        (application as DailyApp).voices.setForeground(true)
        model.refreshToday()
        ReminderScheduler(this).reschedule()
        val widgetIds = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, DailyWidgetReceiver::class.java))
        if (widgetIds.isNotEmpty()) DailyWidget.requestRefresh(this)
    }

    override fun onStop() {
        (application as DailyApp).voices.setForeground(false)
        model.flushOnBackground()
        super.onStop()
    }

    override fun onDestroy() {
        val coordinator = (application as DailyApp).sync
        if (coordinator.editorBarrier === syncBarrier) coordinator.editorBarrier = null
        unregisterReceiver(dateReceiver)
        super.onDestroy()
    }
}
