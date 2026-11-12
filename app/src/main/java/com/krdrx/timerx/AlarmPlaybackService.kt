package com.krdrx.timerx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmPlaybackService : Service() {
    companion object {
        const val ACTION_START = "com.krdrx.timerx.action.START_ALARM"
        const val ACTION_STOP = "com.krdrx.timerx.action.STOP_ALARM"
        private const val CHANNEL_ID = "timer_alarm_playback"
        private const val NOTIFICATION_ID = 7401
    }

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val label = intent.getStringExtra("label") ?: "Timer"
                startForeground(NOTIFICATION_ID, buildNotification(label))
                playSelectedAlarm()
            }
        }
        return START_NOT_STICKY
    }

    private fun playSelectedAlarm() {
        player?.release()
        player = null
        val prefs = getSharedPreferences("timerx", Context.MODE_PRIVATE)
        val uri = prefs.getString("alarm_sound_uri", null)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setOnPreparedListener { it.start() }
            mp.setOnErrorListener { _, _, _ ->
                stopAlarm()
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            stopAlarm()
        }
    }

    private fun buildNotification(label: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 7402,
            Intent(this, AlarmPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 7403, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$"+"label selesai")
            .setContentText("Alarm sedang berbunyi")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "HENTIKAN ALARM", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Kontrol Alarm Timer X", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Kontrol suara alarm Timer X"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    private fun stopAlarm() {
        try {
            player?.let { if (it.isPlaying) it.stop() }
        } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        try {
            player?.let { if (it.isPlaying) it.stop() }
        } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
