package com.example.appdev.platform

import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.UserSettings

/** How the app tells the user about overheating. Implemented by [AlertNotifier]; faked in tests. */
interface AlertPresenter {
    fun showOverheatAlert(snapshot: BatterySnapshot, settings: UserSettings)
    fun cancelOverheatAlert()
    fun vibrate()

    /** Refreshes the persistent monitoring notification. */
    fun updateMonitoring(snapshot: BatterySnapshot?, settings: UserSettings, overheating: Boolean)
}
