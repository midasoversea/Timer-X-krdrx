package com.krdrx.timerx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("timer_id", 0)
        val label = intent.getStringExtra("label") ?: "Timer"
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java)
            .setAction(AlarmPlaybackService.ACTION_START)
            .putExtra("timer_id", id)
            .putExtra("label", label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }
}
