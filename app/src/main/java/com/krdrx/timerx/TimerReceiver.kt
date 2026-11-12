package com.krdrx.timerx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

private const val TIMER_CHANNEL_ID = "timer_done_v2"

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("timer_id", 0)
        val label = intent.getStringExtra("label") ?: "Timer"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android locks a channel's sound configuration after its first creation.
        // Always create the current version with the selected alarm URI before posting.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prefs = context.getSharedPreferences("timerx", Context.MODE_PRIVATE)
            val selectedUri = prefs.getString("alarm_sound_uri", null)?.let { Uri.parse(it) }
            val soundUri = selectedUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val channel = NotificationChannel(
                TIMER_CHANNEL_ID,
                "Alarm Timer X",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Suara alarm ketika hitung mundur selesai"
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                setBypassDnd(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$label selesai")
            .setContentText("Hitung mundur telah berakhir.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setDefaults(0)

        // Pre-Oreo devices use notification sound directly; newer devices use channel sound.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val prefs = context.getSharedPreferences("timerx", Context.MODE_PRIVATE)
            val selectedUri = prefs.getString("alarm_sound_uri", null)?.let { Uri.parse(it) }
            val soundUri = selectedUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            builder.setSound(soundUri)
        }
        manager.notify(id, builder.build())
    }
}
