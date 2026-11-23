package com.example.appdev.domain.profile

import com.example.appdev.domain.model.UserSettings

/** Usage profiles. Each carries its own alert settings; "Custom" (no profile) uses the global ones. */
enum class ProfileType { WORK, NAVIGATION, GAMING, CHARGING }

/** Alert settings owned by a profile. Temperatures in Celsius. */
data class ProfileConfig(
    val thresholdC: Float,
    val warningMarginC: Float,
    val hysteresisC: Float,
    val vibrationEnabled: Boolean,
) {
    fun sanitized() = ProfileConfig(
        thresholdC = UserSettings.sanitizeThreshold(thresholdC),
        warningMarginC = UserSettings.sanitizeWarningMargin(warningMarginC),
        hysteresisC = UserSettings.sanitizeHysteresis(hysteresisC),
        vibrationEnabled = vibrationEnabled,
    )
}

/**
 * Starting values for each profile. They are sensible defaults, not manufacturer limits, and are
 * meant to be adjusted: navigation and gaming tolerate a little more heat before alerting,
 * while charging alerts earlier because charging adds heat.
 */
object ProfileDefaults {
    fun forProfile(profile: ProfileType): ProfileConfig = when (profile) {
        ProfileType.WORK -> ProfileConfig(40f, 3f, 2f, vibrationEnabled = false)
        ProfileType.NAVIGATION -> ProfileConfig(42f, 3f, 2f, vibrationEnabled = true)
        ProfileType.GAMING -> ProfileConfig(43f, 3f, 2.5f, vibrationEnabled = true)
        ProfileType.CHARGING -> ProfileConfig(38f, 2f, 1.5f, vibrationEnabled = true)
    }
}
