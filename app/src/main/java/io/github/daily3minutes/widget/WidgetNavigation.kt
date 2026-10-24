/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.widget

import android.content.Context
import android.content.Intent
import io.github.daily3minutes.ui.MainActivity
import java.util.Locale

/** Explicit destinations shared by the rendered widget and Activity integration tests. */
internal object WidgetNavigation {
    fun intent(context: Context, destination: String, question: Int? = null): Intent {
        require(destination in setOf("today", "history", "more"))
        require(question == null || question in 0..2)
        return Intent(context, MainActivity::class.java)
            // Extras do not distinguish PendingIntents; unique actions keep both buttons separate.
            .setAction("io.github.daily3minutes.OPEN_${destination.uppercase(Locale.ROOT)}_${question ?: "ALL"}")
            .putExtra("destination", destination)
            .putExtra("question", question ?: -1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
