package com.swipewipe.rebuild

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "swipewipe_settings")

data class UserSettings(
    val sortOrder: String = "date_added DESC",
    val confirmDelete: Boolean = true,
    val darkMode: Int = 2,
    val compressQuality: Int = 80,
    val appLock: Boolean = false,
    val graphClientId: String = "",
    val graphSignedIn: Boolean = false,
    val lastSignedInAcct: String = "",
    val deletedToday: Int = 0
)

class SettingsStore(private val context: Context) {

    private object K {
        val SORT = stringPreferencesKey("deck_sort")
        val CONFIRM = booleanPreferencesKey("confirm_delete")
        val THEME = intPreferencesKey("dark_mode")
        val Q = intPreferencesKey("compress_quality")
        val LOCK = booleanPreferencesKey("app_lock")
        val CLIENT_ID = stringPreferencesKey("graph_client_id")
        val SIGNED_IN = booleanPreferencesKey("graph_signed_in")
        val ACCT = stringPreferencesKey("last_account")
        val TODAY = stringPreferencesKey("streak_day")
        val TODAY_COUNT = intPreferencesKey("streak_count")
    }

    val flow: Flow<UserSettings> = context.settingsDataStore.data.map { p ->
        UserSettings(
            sortOrder = p[K.SORT] ?: "date_added DESC",
            confirmDelete = p[K.CONFIRM] ?: true,
            darkMode = p[K.THEME] ?: 2,
            compressQuality = p[K.Q] ?: 80,
            appLock = p[K.LOCK] ?: false,
            graphClientId = p[K.CLIENT_ID] ?: "",
            graphSignedIn = p[K.SIGNED_IN] ?: false,
            lastSignedInAcct = p[K.ACCT] ?: "",
            deletedToday = if (p[K.TODAY] == todayKey()) p[K.TODAY_COUNT] ?: 0 else 0
        )
    }

    private fun todayKey(): String =
        java.time.LocalDate.now().toString()

    suspend fun recordDelete(n: Int = 1) {
        context.settingsDataStore.edit { p ->
            val day = todayKey()
            if (p[K.TODAY] != day) {
                p[K.TODAY] = day
                p[K.TODAY_COUNT] = n
            } else {
                p[K.TODAY_COUNT] = (p[K.TODAY_COUNT] ?: 0) + n
            }
        }
    }

    suspend fun setSortOrder(v: String) = context.settingsDataStore.edit { it[K.SORT] = v }
    suspend fun setConfirmDelete(v: Boolean) = context.settingsDataStore.edit { it[K.CONFIRM] = v }
    suspend fun setDarkMode(v: Int) = context.settingsDataStore.edit { it[K.THEME] = v }
    suspend fun setCompressQuality(v: Int) = context.settingsDataStore.edit { it[K.Q] = v }
    suspend fun setAppLock(v: Boolean) = context.settingsDataStore.edit { it[K.LOCK] = v }
    suspend fun setGraphClientId(v: String) = context.settingsDataStore.edit { it[K.CLIENT_ID] = v.trim() }
    suspend fun setGraphSignedIn(v: Boolean, acct: String = "") =
        context.settingsDataStore.edit {
            it[K.SIGNED_IN] = v
            if (acct.isNotEmpty()) it[K.ACCT] = acct
        }
}
