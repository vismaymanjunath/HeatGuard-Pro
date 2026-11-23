package com.example.appdev.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AiSettings(
    /** The user has read what is sent to the AI provider and agreed to it. */
    val consentGiven: Boolean = false,
    /** Include live phone data with each question. If off, the assistant only gets the question. */
    val includePhoneData: Boolean = true,
)

class AiSettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AiSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { AiSettings(it[CONSENT] ?: false, it[INCLUDE_DATA] ?: true) }
        .distinctUntilChanged()

    suspend fun setConsent(given: Boolean) {
        dataStore.edit { it[CONSENT] = given }
    }

    suspend fun setIncludePhoneData(include: Boolean) {
        dataStore.edit { it[INCLUDE_DATA] = include }
    }

    private companion object {
        val CONSENT = booleanPreferencesKey("ai_consent")
        val INCLUDE_DATA = booleanPreferencesKey("ai_include_phone_data")
    }
}
