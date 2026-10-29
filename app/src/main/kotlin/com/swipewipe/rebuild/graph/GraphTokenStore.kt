package com.swipewipe.rebuild.graph

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GraphTokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "swipewipe_graph_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(access: String, refresh: String, expiresAt: Long, account: String) {
        prefs.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .putLong("expires_at", expiresAt)
            .putString("account", account)
            .apply()
    }

    fun access(): String? = prefs.getString("access", null)
    fun refresh(): String? = prefs.getString("refresh", null)
    fun expiresAt(): Long = prefs.getLong("expires_at", 0)
    fun account(): String? = prefs.getString("account", null)

    fun clear() = prefs.edit().clear().apply()
}
