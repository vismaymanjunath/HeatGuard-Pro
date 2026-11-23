package com.example.appdev.domain.model

import com.example.appdev.domain.profile.ProfileType
import kotlin.math.roundToInt

/**
 * User-configurable preferences. Temperatures are always stored in Celsius.
 *
 * [thresholdC], [warningMarginC], [hysteresisC] and [vibrationEnabled] are the *effective* values:
 * when a profile is active they are that profile's values.
 */
data class UserSettings(
    val thresholdC: Float = DEFAULT_THRESHOLD_C,
    val warningMarginC: Float = DEFAULT_WARNING_MARGIN_C,
    /** An alert stays active until the temperature drops this far below the threshold. */
    val hysteresisC: Float = DEFAULT_HYSTERESIS_C,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val monitoringEnabled: Boolean = false,
    /** Null means "Custom": the global values above are in effect. Otherwise they come from the profile. */
    val activeProfile: ProfileType? = null,
) {
    /** Temperature at which the indicator turns orange. */
    val warningStartC: Float get() = thresholdC - warningMarginC

    /** Temperature at or below which an active overheating incident is considered resolved. */
    val clearBelowC: Float get() = thresholdC - hysteresisC

    companion object {
        const val DEFAULT_THRESHOLD_C = 40f
        const val MIN_THRESHOLD_C = 30f
        const val MAX_THRESHOLD_C = 55f
        const val THRESHOLD_STEP_C = 0.5f

        const val DEFAULT_WARNING_MARGIN_C = 3f
        const val MIN_WARNING_MARGIN_C = 1f
        const val MAX_WARNING_MARGIN_C = 8f
        const val WARNING_MARGIN_STEP_C = 0.5f

        const val DEFAULT_HYSTERESIS_C = 2f
        const val MIN_HYSTERESIS_C = 0.5f
        const val MAX_HYSTERESIS_C = 5f
        const val HYSTERESIS_STEP_C = 0.5f

        /** Clamps to the supported range and snaps to the 0.5 °C step. Non-finite → default. */
        fun sanitizeThreshold(valueC: Float): Float =
            sanitize(valueC, MIN_THRESHOLD_C, MAX_THRESHOLD_C, THRESHOLD_STEP_C, DEFAULT_THRESHOLD_C)

        fun sanitizeWarningMargin(valueC: Float): Float =
            sanitize(valueC, MIN_WARNING_MARGIN_C, MAX_WARNING_MARGIN_C, WARNING_MARGIN_STEP_C, DEFAULT_WARNING_MARGIN_C)

        fun sanitizeHysteresis(valueC: Float): Float =
            sanitize(valueC, MIN_HYSTERESIS_C, MAX_HYSTERESIS_C, HYSTERESIS_STEP_C, DEFAULT_HYSTERESIS_C)

        private fun sanitize(value: Float, min: Float, max: Float, step: Float, fallback: Float): Float {
            if (!value.isFinite()) return fallback
            val snapped = (value / step).roundToInt() * step
            return snapped.coerceIn(min, max)
        }
    }
}
