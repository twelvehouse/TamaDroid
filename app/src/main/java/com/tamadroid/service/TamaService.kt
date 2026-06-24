package com.tamadroid.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tamadroid.core.TamaCore
import com.tamadroid.widget.TamaWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the pet alive in the background (recent Android stops
 * background work unless a foreground service with an ongoing notification is running —
 * the AdGuard pattern). Owns nothing itself; delegates the emulator to [TamaRuntime].
 *
 * Also watches the attention icon (index 7) and posts a "call" notification + custom SE
 * on its rising edge.
 */
class TamaService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        TamaNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            TamaNotifications.NOTIF_ALIVE,
            TamaNotifications.ongoing(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (!TamaRuntime.ensureStarted(this)) {
            stopSelf()   // no ROM imported yet
            return START_NOT_STICKY
        }
        observeAttention()
        observeWidget()
        return START_STICKY
    }

    private var widgetting = false
    private fun observeWidget() {
        if (widgetting) return
        widgetting = true
        scope.launch {
            val fb = ByteArray(TamaCore.FRAME_BYTES)
            val icons = ByteArray(TamaCore.ICON_NUM)
            while (isActive) {
                if (TamaRuntime.isRunning && TamaWidgetProvider.hasWidgets(this@TamaService)) {
                    // Vsync frame = only fully-settled (coherent) frames, so even at a
                    // slow 1 s cadence the widget never shows a half-drawn frame.
                    TamaCore.nativeGetVsyncFrame(fb, icons)
                    TamaWidgetProvider.pushFrame(this@TamaService, fb.copyOf())
                }
                delay(WIDGET_REFRESH_MS)
            }
        }
    }

    private var observing = false
    private fun observeAttention() {
        if (observing) return
        observing = true
        val flow = TamaRuntime.frame ?: return
        scope.launch {
            // The attention icon BLINKS while the pet is calling, so a naive rising-edge
            // check fires postCall (and replays the alert sound) on every blink. Debounce:
            // treat continuous blinking as one call and notify once. A genuinely new call
            // (icon quiet for longer than NEW_CALL_GAP_MS) alerts again.
            var lastOn = 0L
            flow.collect { f ->
                if (f.icons.getOrNull(ATTENTION_ICON)?.toInt() != 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastOn > NEW_CALL_GAP_MS) TamaNotifications.postCall(this@TamaService)
                    lastOn = now
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ATTENTION_ICON = 7   // icon8 = attention/call (Pebble special-cases this)
        private const val NEW_CALL_GAP_MS = 5000L   // quiet gap before the call icon counts as a new call
        private const val WIDGET_REFRESH_MS = 500L   // fixed; Vsync keeps frames coherent

        fun start(ctx: Context) {
            val i = Intent(ctx, TamaService::class.java)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TamaService::class.java))
        }
    }
}
