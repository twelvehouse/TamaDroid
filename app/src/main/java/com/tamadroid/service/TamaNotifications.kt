package com.tamadroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.tamadroid.MainActivity
import com.tamadroid.R

/** Notification channels + builders for the always-alive service and the "call" alert. */
object TamaNotifications {
    const val CHANNEL_ALIVE = "alive"
    const val CHANNEL_CALL = "call"
    const val NOTIF_ALIVE = 1
    const val NOTIF_CALL = 2

    /** Minimum spacing between call alerts. One call animates the attention icon for a while, so
     *  this both de-dupes the burst into a single sound and serves as a reminder interval. */
    private const val MIN_CALL_INTERVAL_MS = 60_000L   // 1 minute

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)

        // Ongoing, silent: keeps the process alive in the background.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALIVE, "Pet running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps your Tamagotchi alive in the background."
                setShowBadge(false)
            }
        )

        // High-importance call alert with the custom beep.
        val callSound = Uri.parse("android.resource://${ctx.packageName}/${R.raw.tama_call}")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALL, "Pet is calling", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Your Tamagotchi needs attention."
                setSound(
                    callSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
        )
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ongoing(ctx: Context): Notification {
        val stop = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, TamaStopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ALIVE)
            .setSmallIcon(R.drawable.ic_stat_tama)
            .setContentTitle("Tamagotchi running")
            .setContentText("Keeping your pet alive in the background")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(ctx))
            .addAction(0, "Stop", stop)
            .build()
    }

    /** When the last call alert was actually posted (process-global rate-limit anchor). */
    @Volatile private var lastCallMs = 0L

    fun postCall(ctx: Context) {
        // The point of this alert is to notice — while the app is closed — that the pet needs
        // attention. A single call makes the attention icon animate on/off for many seconds, which
        // produces a burst of rising edges; without this guard each one replays the HIGH-importance
        // sound (one notification, sound N times). Rate-limit so a call alerts at most once per
        // window, which also doubles as a gentle "still calling" reminder if the pet is ignored.
        val now = System.currentTimeMillis()
        if (now - lastCallMs < MIN_CALL_INTERVAL_MS) return
        lastCallMs = now

        val n = NotificationCompat.Builder(ctx, CHANNEL_CALL)
            .setSmallIcon(R.drawable.ic_stat_tama)
            .setContentTitle("Your Tamagotchi is calling!")
            .setContentText("It needs your care")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_CALL, n)
    }
}
