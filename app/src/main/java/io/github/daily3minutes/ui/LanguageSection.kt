// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.daily3minutes.widget.DailyWidget

@Composable
fun LanguageSection(enabled: Boolean) {
    val context = LocalContext.current
    val current = AppLocale.preference(context)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("language-section")) {
        Text(t("语言"), style = MaterialTheme.typography.titleMedium)
        listOf("system" to t("跟随系统"), "zh" to "简体中文", "en" to "English").forEach { (code, label) ->
            Row {
                RadioButton(selected = code == current, enabled = enabled,
                    modifier = Modifier.testTag("language-$code"), onClick = {
                        AppLocale.set(context, code)
                        DailyWidget.requestRefresh(context)
                        if (android.os.Build.VERSION.SDK_INT < 33 || (context as? Activity)?.isChangingConfigurations == false)
                            (context as? Activity)?.recreate()
                    })
                Text(label, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
