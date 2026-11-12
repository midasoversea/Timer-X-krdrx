package com.krdrx.timerx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("timer_id", 0)
        val label = intent.getStringExtra("label") ?: "Timer"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("timer_finished_v1", "Timer selesai", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pemberitahuan ketika hitung mundur selesai"
                setSound(null, null)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(context, 8100 + id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "timer_finished_v1")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$label selesai")
            .setContentText("Alarm sedang berbunyi")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(openIntent)
            .setVibrate(longArrayOf(0, 400, 250, 400))
            .build()
        manager.notify(8100 + id, notification)

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java)
            .setAction(AlarmPlaybackService.ACTION_START)
            .putExtra("timer_id", id)
            .putExtra("label", label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }
}
