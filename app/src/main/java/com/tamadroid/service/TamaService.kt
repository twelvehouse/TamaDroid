package com.tamadroid.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tamadroid.core.TamaCore
import com.tamadroid.widget.TamaWidgetProvider
import com.tamadroid.widget.WidgetPrefs
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
                    // Anti-tearing (default on): read the SETTLE frame — never partial, but
                    // skips 30 Hz animations. Off: the live frame (animates, may briefly tear).
                    if (WidgetPrefs.antiTear(this@TamaService)) TamaCore.nativeGetSettleFrame(fb, icons)
                    else TamaCore.nativeGetFrame(fb, icons)
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
            // Fire on the rising edge of the attention icon (off -> on). During a single call the
            // firmware animates this icon on/off every few seconds, producing many rising edges —
            // but [TamaNotifications.postCall] rate-limits the alert, so one call yields one sound.
            // (See postCall: the alert channel is IMPORTANCE_HIGH with a custom sound, so every
            // notify() would otherwise replay it even though they collapse into one notification.)
            var prev = false
            flow.collect { f ->
                val calling = f.icons.getOrNull(ATTENTION_ICON)?.toInt() != 0
                if (calling && !prev) TamaNotifications.postCall(this@TamaService)
                prev = calling
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
        // Home-screen widgets update via RemoteViews IPC to the launcher, so true 30 fps
        // isn't feasible; 200 ms (~5 fps) is a practical ceiling. Frames are already
        // coherent (run loop publishes whole frames), so faster never tears.
        private const val WIDGET_REFRESH_MS = 200L

        fun start(ctx: Context) {
            val i = Intent(ctx, TamaService::class.java)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TamaService::class.java))
        }
    }
}
