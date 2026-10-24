/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daily3minutes.reminder.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPrivacyDefaultsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun isolatedReminderSettingsDefaultToOffAtNinePm() {
        // Only a dedicated test preferences file is cleared; application settings/records are untouched.
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("instrumentation_widget_$name", mode)
        }
        val preferences = isolated.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val scheduler = ReminderScheduler(isolated)
            assertFalse(scheduler.isEnabled())
            assertEquals(21, scheduler.hour())
            assertEquals(0, scheduler.minute())
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Suppress("DEPRECATION")
    @Test fun optionalGoogleSyncRequestsNetworkButNoPersonalDataPermissions() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        listOf(
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_EXACT_ALARM",
        ).forEach { permission -> assertFalse("Unexpected permission: $permission", permission in requested) }
        org.junit.Assert.assertTrue("Google Tasks requires HTTPS", "android.permission.INTERNET" in requested)
    }
}
