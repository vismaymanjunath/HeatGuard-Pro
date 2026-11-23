package com.example.appdev.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.profile.ProfileConfig
import com.example.appdev.domain.profile.ProfileDefaults
import com.example.appdev.domain.profile.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "heatguard_settings")

/** Persists [UserSettings] in Preferences DataStore. Values are sanitised on read and write. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)
        .distinctUntilChanged()

    // The threshold, bands, hysteresis and vibration edit the active profile if there is one,
    // otherwise the global ("Custom") values.

    suspend fun setThreshold(celsius: Float) {
        dataStore.edit { prefs ->
            val value = UserSettings.sanitizeThreshold(celsius)
            val profile = activeProfile(prefs)
            if (profile != null) prefs[Keys.threshold(profile)] = value else prefs[Keys.THRESHOLD_C] = value
        }
    }

    suspend fun setWarningMargin(celsius: Float) {
        dataStore.edit { prefs ->
            val value = UserSettings.sanitizeWarningMargin(celsius)
            val profile = activeProfile(prefs)
            if (profile != null) prefs[Keys.margin(profile)] = value else prefs[Keys.WARNING_MARGIN_C] = value
        }
    }

    suspend fun setHysteresis(celsius: Float) {
        dataStore.edit { prefs ->
            val value = UserSettings.sanitizeHysteresis(celsius)
            val profile = activeProfile(prefs)
            if (profile != null) prefs[Keys.hysteresis(profile)] = value else prefs[Keys.HYSTERESIS_C] = value
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = activeProfile(prefs)
            if (profile != null) prefs[Keys.vibration(profile)] = enabled else prefs[Keys.VIBRATION] = enabled
        }
    }

    /** Selects a profile, or null for Custom. */
    suspend fun setActiveProfile(profile: ProfileType?) {
        dataStore.edit {
            if (profile == null) it.remove(Keys.ACTIVE_PROFILE) else it[Keys.ACTIVE_PROFILE] = profile.name
        }
    }

    suspend fun setUnit(unit: TemperatureUnit) {
        dataStore.edit { it[Keys.UNIT] = unit.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MONITORING] = enabled }
    }

    /**
     * Restores threshold, bands, unit, alert preferences and every profile to their defaults and
     * switches to Custom. The monitoring on/off state is kept.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            listOf(
                Keys.THRESHOLD_C, Keys.WARNING_MARGIN_C, Keys.HYSTERESIS_C, Keys.UNIT,
                Keys.NOTIFICATIONS, Keys.VIBRATION, Keys.ACTIVE_PROFILE,
            ).forEach { prefs.remove(it) }
            ProfileType.entries.forEach { p ->
                prefs.remove(Keys.threshold(p))
                prefs.remove(Keys.margin(p))
                prefs.remove(Keys.hysteresis(p))
                prefs.remove(Keys.vibration(p))
            }
        }
    }

    private fun activeProfile(prefs: Preferences): ProfileType? =
        prefs[Keys.ACTIVE_PROFILE]?.let { name -> ProfileType.entries.firstOrNull { it.name == name } }

    private fun toSettings(prefs: Preferences): UserSettings {
        val defaults = UserSettings()
        val profile = activeProfile(prefs)
        val effective = if (profile != null) {
            val d = ProfileDefaults.forProfile(profile)
            ProfileConfig(
                thresholdC = prefs[Keys.threshold(profile)] ?: d.thresholdC,
                warningMarginC = prefs[Keys.margin(profile)] ?: d.warningMarginC,
                hysteresisC = prefs[Keys.hysteresis(profile)] ?: d.hysteresisC,
                vibrationEnabled = prefs[Keys.vibration(profile)] ?: d.vibrationEnabled,
            ).sanitized()
        } else {
            ProfileConfig(
                thresholdC = prefs[Keys.THRESHOLD_C] ?: defaults.thresholdC,
                warningMarginC = prefs[Keys.WARNING_MARGIN_C] ?: defaults.warningMarginC,
                hysteresisC = prefs[Keys.HYSTERESIS_C] ?: defaults.hysteresisC,
                vibrationEnabled = prefs[Keys.VIBRATION] ?: defaults.vibrationEnabled,
            ).sanitized()
        }
        return UserSettings(
            thresholdC = effective.thresholdC,
            warningMarginC = effective.warningMarginC,
            hysteresisC = effective.hysteresisC,
            activeProfile = profile,
            unit = prefs[Keys.UNIT]
                ?.let { name -> TemperatureUnit.entries.firstOrNull { it.name == name } }
                ?: defaults.unit,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: defaults.notificationsEnabled,
            vibrationEnabled = effective.vibrationEnabled,
            monitoringEnabled = prefs[Keys.MONITORING] ?: defaults.monitoringEnabled,
        )
    }

    private object Keys {
        val THRESHOLD_C = floatPreferencesKey("threshold_c")
        val WARNING_MARGIN_C = floatPreferencesKey("warning_margin_c")
        val HYSTERESIS_C = floatPreferencesKey("hysteresis_c")
        val UNIT = stringPreferencesKey("temperature_unit")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val MONITORING = booleanPreferencesKey("monitoring_enabled")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")

        fun threshold(p: ProfileType) = floatPreferencesKey("profile_${p.name.lowercase()}_threshold_c")
        fun margin(p: ProfileType) = floatPreferencesKey("profile_${p.name.lowercase()}_margin_c")
        fun hysteresis(p: ProfileType) = floatPreferencesKey("profile_${p.name.lowercase()}_hysteresis_c")
        fun vibration(p: ProfileType) = booleanPreferencesKey("profile_${p.name.lowercase()}_vibration")
    }
}
