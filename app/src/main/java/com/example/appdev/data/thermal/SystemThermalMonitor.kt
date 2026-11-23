package com.example.appdev.data.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** A stream of what Android reports about thermal state. */
fun interface ThermalSystemSource {
    fun state(): Flow<SystemThermalState>
}

/** Used when no system source exists (tests, previews). Reports everything as unavailable. */
object NoThermalSystemSource : ThermalSystemSource {
    override fun state(): Flow<SystemThermalState> = kotlinx.coroutines.flow.flowOf(SystemThermalState.Unknown)
}

/**
 * Reads PowerManager's thermal APIs where the OS supports them:
 * - thermal status (API 29+): event-driven through a listener;
 * - thermal headroom (API 30+): polled, because the API has no callback. Many devices return NaN
 *   (unsupported), which is surfaced as "not reported" rather than a made-up number.
 *
 * Anything that throws (some OEM builds do) is treated as unavailable.
 */
class SystemThermalMonitor(
    context: Context,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) : ThermalSystemSource {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun state(): Flow<SystemThermalState> = callbackFlow {
        val pm = powerManager
        val statusApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null
        val headroomApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm != null

        val current = AtomicReference(
            SystemThermalState(
                statusApiAvailable = statusApi,
                status = if (statusApi) readStatus(pm!!) else null,
                headroomApiAvailable = headroomApi,
            ),
        )
        fun update(transform: (SystemThermalState) -> SystemThermalState) {
            trySend(current.updateAndGet { transform(it) })
        }

        var listener: PowerManager.OnThermalStatusChangedListener? = null
        if (statusApi) {
            listener = registerStatusListener(pm!!) { raw ->
                update { it.copy(status = ThermalStatus.fromAndroid(raw)) }
            }
        }

        val poller = launch {
            while (isActive) {
                update { state ->
                    if (headroomApi) {
                        state.copy(
                            headroomNow = readHeadroom(pm!!, 0),
                            headroomForecast = readHeadroom(pm, FORECAST_SECONDS),
                        )
                    } else {
                        state
                    }
                }
                delay(pollIntervalMillis)
            }
        }

        awaitClose {
            poller.cancel()
            if (listener != null && pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                removeStatusListener(pm, listener)
            }
        }
    }.conflate()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readStatus(pm: PowerManager): ThermalStatus? =
        runCatching { ThermalStatus.fromAndroid(pm.currentThermalStatus) }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun registerStatusListener(pm: PowerManager, onChange: (Int) -> Unit): PowerManager.OnThermalStatusChangedListener? {
        val listener = PowerManager.OnThermalStatusChangedListener { onChange(it) }
        return runCatching {
            pm.addThermalStatusListener(ContextCompat.getMainExecutor(appContext), listener)
            listener
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun removeStatusListener(pm: PowerManager, listener: PowerManager.OnThermalStatusChangedListener) {
        runCatching { pm.removeThermalStatusListener(listener) }
    }

    /** Returns null for NaN (unsupported / rate limited), negative or otherwise invalid values. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readHeadroom(pm: PowerManager, forecastSeconds: Int): Float? =
        runCatching { pm.getThermalHeadroom(forecastSeconds) }.getOrNull()
            ?.takeIf { it.isFinite() && it >= 0f }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
        const val FORECAST_SECONDS = 10
    }
}
