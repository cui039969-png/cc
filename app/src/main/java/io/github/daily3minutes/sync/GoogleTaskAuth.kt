// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

object GoogleTaskAuth {
    const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
    fun request(email: String) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(TASKS_SCOPE)))
        .setAccount(Account(email, "com.google"))
        .build()

    suspend fun token(context: Context, email: String): String {
        val result = Identity.getAuthorizationClient(context).authorize(request(email)).await()
        if (result.hasResolution() || TASKS_SCOPE !in result.grantedScopes) throw TasksAuthRequired()
        return result.accessToken?.takeIf { it.isNotBlank() } ?: throw TasksAuthRequired()
    }
    suspend fun invalidate(context: Context, token: String) {
        Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }
}
