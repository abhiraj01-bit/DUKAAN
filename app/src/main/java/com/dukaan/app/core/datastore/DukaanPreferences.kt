package com.dukaan.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.dukaan.app.core.security.KeystoreHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dukaan_prefs")

@Singleton
class DukaanPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_SHOP_PROFILE_ID = stringPreferencesKey("shop_profile_id")
        val KEY_LANGUAGE = stringPreferencesKey("language_code")
        val KEY_NOTIF_LOW_STOCK = booleanPreferencesKey("notif_low_stock")
        val KEY_NOTIF_PAYMENT_REMINDER = booleanPreferencesKey("notif_payment_reminder")
        val KEY_NOTIF_DAILY_SUMMARY = booleanPreferencesKey("notif_daily_summary")
        val KEY_NOTIF_BACKUP_REMINDER = booleanPreferencesKey("notif_backup_reminder")
        val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val KEY_ALLOW_NEGATIVE_STOCK = booleanPreferencesKey("allow_negative_stock")
        val KEY_DEFAULT_TAX_PERCENT = intPreferencesKey("default_tax_percent")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val KEY_GEMINI_FALLBACK_ENABLED = booleanPreferencesKey("gemini_fallback_enabled")
        val KEY_LAST_USED_MODEL = stringPreferencesKey("last_used_model")
        val KEY_LAST_SCAN_STATUS = stringPreferencesKey("last_scan_status")
        val KEY_LAST_SCAN_DURATION_MS = longPreferencesKey("last_scan_duration_ms")
        val KEY_LAST_SCAN_ERROR = stringPreferencesKey("last_scan_error")
        val KEY_APP_PIN = stringPreferencesKey("app_pin")
    }

    val appPin: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_APP_PIN] ?: "" }

    suspend fun setAppPin(pin: String) {
        dataStore.edit { it[KEY_APP_PIN] = pin }
    }

    val geminiApiKey: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val stored = prefs[KEY_GEMINI_API_KEY] ?: ""
            KeystoreHelper.decrypt(stored)
        }

    val geminiModel: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GEMINI_MODEL] ?: "gemini-1.5-flash" }

    val geminiFallbackEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GEMINI_FALLBACK_ENABLED] ?: true }

    val lastUsedModel: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_USED_MODEL] ?: "" }

    val lastScanStatus: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_SCAN_STATUS] ?: "No scans yet" }

    val lastScanDurationMs: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_SCAN_DURATION_MS] ?: 0L }

    val lastScanError: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_SCAN_ERROR] ?: "" }

    suspend fun setGeminiApiKey(apiKey: String) {
        dataStore.edit {
            it[KEY_GEMINI_API_KEY] = KeystoreHelper.encrypt(apiKey)
        }
    }

    suspend fun setGeminiModel(model: String) {
        dataStore.edit { it[KEY_GEMINI_MODEL] = model }
    }

    suspend fun setGeminiFallbackEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_GEMINI_FALLBACK_ENABLED] = enabled }
    }

    suspend fun updateScanDiagnostics(modelUsed: String, status: String, durationMs: Long, error: String = "") {
        dataStore.edit {
            it[KEY_LAST_USED_MODEL] = modelUsed
            it[KEY_LAST_SCAN_STATUS] = status
            it[KEY_LAST_SCAN_DURATION_MS] = durationMs
            it[KEY_LAST_SCAN_ERROR] = error
        }
    }

    val isOnboardingDone: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ONBOARDING_DONE] ?: false }

    val languageCode: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LANGUAGE] ?: "en" }

    val allowNegativeStock: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ALLOW_NEGATIVE_STOCK] ?: false }

    val notifLowStock: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_NOTIF_LOW_STOCK] ?: true }

    val notifPaymentReminder: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_NOTIF_PAYMENT_REMINDER] ?: true }

    val notifDailySummary: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_NOTIF_DAILY_SUMMARY] ?: false }

    val lastBackupTime: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_BACKUP_TIME] ?: 0L }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setLanguage(code: String) {
        dataStore.edit { it[KEY_LANGUAGE] = code }
    }

    suspend fun setAllowNegativeStock(allow: Boolean) {
        dataStore.edit { it[KEY_ALLOW_NEGATIVE_STOCK] = allow }
    }

    suspend fun setNotifLowStock(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_LOW_STOCK] = enabled }
    }

    suspend fun setNotifPaymentReminder(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_PAYMENT_REMINDER] = enabled }
    }

    suspend fun setNotifDailySummary(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_DAILY_SUMMARY] = enabled }
    }

    suspend fun setLastBackupTime(time: Long) {
        dataStore.edit { it[KEY_LAST_BACKUP_TIME] = time }
    }
}
