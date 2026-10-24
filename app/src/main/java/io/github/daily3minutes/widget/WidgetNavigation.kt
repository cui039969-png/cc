/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.widget

import android.content.Context
import android.content.Intent
import io.github.daily3minutes.ui.MainActivity
import java.util.Locale

/** Explicit destinations shared by the rendered widget and Activity integration tests. */
internal object WidgetNavigation {
    fun intent(context: Context, destination: String): Intent {
        require(destination == "today" || destination == "history")
        return Intent(context, MainActivity::class.java)
            // Extras do not distinguish PendingIntents; unique actions keep both buttons separate.
            .setAction("io.github.daily3minutes.OPEN_${destination.uppercase(Locale.ROOT)}")
            .putExtra("destination", destination)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
