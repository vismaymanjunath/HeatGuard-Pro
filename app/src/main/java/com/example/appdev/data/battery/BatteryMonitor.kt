package com.example.appdev.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.appdev.domain.model.BatterySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A stream of battery readings. The real implementation reads Android's battery broadcast;
 * Demo Mode will provide another implementation that feeds the same pipeline.
 */
fun interface BatterySnapshotSource {
    fun snapshots(): Flow<BatterySnapshot>
}

/**
 * Reads the system's sticky ACTION_BATTERY_CHANGED broadcast.
 *
 * Android only sends this broadcast when a battery value changes, so the monitor also re-reads
 * the sticky intent every [pollIntervalMillis] to keep "last updated" times honest while the
 * temperature is stable. No permission is required.
 */
class BatteryMonitor(
    context: Context,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : BatterySnapshotSource {

    private val appContext = context.applicationContext
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    /** Synchronously reads the latest sticky battery intent, or null if none is available. */
    fun readNow(): BatterySnapshot? =
        appContext.registerReceiver(null, filter)?.let(::toSnapshot)

    override fun snapshots(): Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(toSnapshot(intent))
            }
        }
        // System broadcasts are delivered regardless of the exported flag; NOT_EXPORTED keeps
        // other apps from spoofing battery data into this receiver.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val poller = launch {
            while (isActive) {
                delay(pollIntervalMillis)
                readNow()?.let { trySend(it) }
            }
        }

        awaitClose {
            poller.cancel()
            appContext.unregisterReceiver(receiver)
        }
    }.conflate()

    private fun toSnapshot(intent: Intent): BatterySnapshot =
        BatteryParser.parse(intent.toRawExtras(), clock())

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
    }
}

internal fun Intent.toRawExtras(): RawBatteryExtras = RawBatteryExtras(
    temperatureTenthsC = intExtraOrNull(BatteryManager.EXTRA_TEMPERATURE),
    level = intExtraOrNull(BatteryManager.EXTRA_LEVEL),
    scale = intExtraOrNull(BatteryManager.EXTRA_SCALE),
    status = intExtraOrNull(BatteryManager.EXTRA_STATUS),
    plugged = intExtraOrNull(BatteryManager.EXTRA_PLUGGED),
    voltage = intExtraOrNull(BatteryManager.EXTRA_VOLTAGE),
    health = intExtraOrNull(BatteryManager.EXTRA_HEALTH),
    technology = getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
    present = if (hasExtra(BatteryManager.EXTRA_PRESENT)) {
        getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
    } else {
        null
    },
)

private fun Intent.intExtraOrNull(key: String): Int? =
    if (hasExtra(key)) getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null
