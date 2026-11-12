package com.krdrx.timerx

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max

private const val TEAL = 0xFF00AFA5.toInt()
private const val TEAL_DARK = 0xFF35D0C3.toInt()

data class TimerItem(
    val id: Int,
    var title: String,
    var durationMs: Long,
    var remainingMs: Long,
    var endAt: Long = 0L,
    var running: Boolean = false,
    var finished: Boolean = false
)

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("timerx", MODE_PRIVATE) }
    private val timers = mutableListOf<TimerItem>()
    private val labels = mutableMapOf<Int, TextView>()
    private val handlers = mutableMapOf<Int, CountDownTimer>()
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private var dark = false
    private var nextId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = prefs.getBoolean("dark", false)
        loadTimers()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        render()
        timers.filter { it.running }.forEach { item ->
            if (item.endAt <= System.currentTimeMillis()) finishTimer(item) else schedule(item)
        }
    }

    override fun onDestroy() {
        handlers.values.forEach { it.cancel() }
        super.onDestroy()
    }

    private fun render() {
        val bg = if (dark) 0xFF101817.toInt() else 0xFFF4F7F7.toInt()
        val panel = if (dark) 0xFF1B2826.toInt() else 0xFFFFFFFF.toInt()
        val primary = if (dark) 0xFFE8F1F0.toInt() else 0xFF172321.toInt()
        val secondary = if (dark) 0xFF9CB0AC.toInt() else 0xFF667773.toInt()
        val accent = if (dark) TEAL_DARK else TEAL

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(100))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(TextView(this).apply {
            text = "Timer X"
            textSize = 28f
            setTextColor(primary)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = if (dark) "☀" else "☾"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(panel, dp(18))
            contentDescription = "Ganti tema terang atau gelap"
            setOnClickListener {
                dark = !dark
                prefs.edit().putBoolean("dark", dark).apply()
                render()
            }
        })
        content.addView(header)
        val soundRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(4))
        }
        soundRow.addView(TextView(this).apply {
            text = "Nada: " + selectedSoundName()
            textSize = 13f
            setTextColor(secondary)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        soundRow.addView(TextView(this).apply {
            text = "GANTI NADA"
            textSize = 11f
            setTextColor(accent)
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { chooseAlarmSound() }
        })
        content.addView(soundRow)

        content.addView(TextView(this).apply {
            text = "TIMER AKTIF"
            textSize = 11f
            letterSpacing = .12f
            setTextColor(accent)
            setPadding(0, dp(28), 0, dp(12))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        empty = TextView(this).apply {
            text = "Belum ada timer.\nTekan tombol + untuk membuat timer pertama."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(secondary)
            setPadding(dp(24), dp(70), dp(24), dp(50))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(empty, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        root.addView(TextView(this).apply {
            text = "+"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(accent, dp(28))
            elevation = dp(8).toFloat()
            contentDescription = "Tambah timer"
            setOnClickListener { showAddDialog() }
        }, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(22), dp(24))
        })

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 26) window.decorView.systemUiVisibility =
            if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(root)
        rebuildRows()
    }

    private fun rebuildRows() {
        list.removeAllViews()
        labels.clear()
        empty.visibility = if (timers.isEmpty()) View.VISIBLE else View.GONE
        timers.forEach { item ->
            val panel = if (dark) 0xFF1B2826.toInt() else 0xFFFFFFFF.toInt()
            val primary = if (dark) 0xFFE8F1F0.toInt() else 0xFF172321.toInt()
            val accent = if (dark) TEAL_DARK else TEAL
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = rounded(panel, dp(18))
                elevation = dp(2).toFloat()
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(this).apply {
                text = item.title
                textSize = 16f
                setTextColor(primary)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(TextView(this).apply {
                text = when { item.finished -> "SELESAI"; item.running -> "BERJALAN"; else -> "DIJEDA" }
                textSize = 10f
                setTextColor(accent)
            })
            card.addView(top)
            val time = TextView(this).apply {
                text = formatTime(currentRemaining(item))
                textSize = 34f
                setTextColor(accent)
                typeface = android.graphics.Typeface.create("sans-serif-light", 0)
                setPadding(0, dp(8), 0, dp(8))
            }
            card.addView(time)
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            fun action(text: String, callback: () -> Unit) {
                actions.addView(TextView(this@MainActivity).apply {
                    this.text = text
                    textSize = 13f
                    setTextColor(accent)
                    setPadding(dp(12), dp(8), dp(8), dp(8))
                    setOnClickListener { callback() }
                })
            }
            if (item.running) action("JEDA") { pause(item) }
            else if (!item.finished) action("MULAI") { start(item) }
            else action("ULANGI") { reset(item); start(item) }
            if (!item.running && !item.finished) action("RESET") { reset(item) }
            action("HAPUS") { delete(item) }
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            labels[item.id] = time
        }
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(4))
        }
        val name = EditText(this).apply {
            hint = "Nama timer"
            setSingleLine(true)
        }
        val hours = numberInput()
        val minutes = numberInput()
        val seconds = numberInput()
        box.addView(name)
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(hours, minutes, seconds).forEachIndexed { index, input ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this@MainActivity).apply {
                text = listOf("JAM", "MENIT", "DETIK")[index]
                textSize = 10f
            })
            col.addView(input, LinearLayout.LayoutParams(-1, dp(54)))
            timeRow.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        }
        box.addView(timeRow)
        AlertDialog.Builder(this)
            .setTitle("Tambah timer")
            .setView(box)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Buat") { _, _ ->
                val h = hours.text.toString().toLongOrNull() ?: 0L
                val m = minutes.text.toString().toLongOrNull() ?: 0L
                val s = seconds.text.toString().toLongOrNull() ?: 0L
                val duration = ((h * 3600L) + (m * 60L) + s) * 1000L
                if (duration <= 0L) {
                    Toast.makeText(this, "Durasi harus lebih dari 0 detik.", Toast.LENGTH_SHORT).show()
                } else {
                    val item = TimerItem(nextId++, name.text.toString().trim().ifEmpty { "Timer ${timers.size + 1}" }, duration, duration)
                    timers.add(item)
                    persist()
                    rebuildRows()
                    start(item)
                }
            }.show()
    }

    private fun numberInput() = EditText(this).apply {
        hint = "0"
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
    }

    private fun alarmIntent(item: TimerItem): PendingIntent {
        val intent = Intent(this, TimerReceiver::class.java)
            .putExtra("timer_id", item.id)
            .putExtra("label", item.title)
        return PendingIntent.getBroadcast(
            this, item.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarm(item: TimerItem) {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.endAt, alarmIntent(item))
    }

    private fun cancelAlarm(item: TimerItem) {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(alarmIntent(item))
    }

    private fun start(item: TimerItem) {
        if (item.finished) reset(item)
        item.running = true
        item.endAt = System.currentTimeMillis() + item.remainingMs
        schedule(item)
        persist()
        rebuildRows()
    }

    private fun schedule(item: TimerItem) {
        handlers[item.id]?.cancel()
        scheduleAlarm(item)
        val millis = max(0L, item.endAt - System.currentTimeMillis())
        handlers[item.id] = object : CountDownTimer(millis, 250L) {
            override fun onTick(until: Long) {
                item.remainingMs = until
                labels[item.id]?.text = formatTime(until)
            }
            override fun onFinish() { finishTimer(item) }
        }.start()
    }

    private fun pause(item: TimerItem) {
        item.remainingMs = currentRemaining(item)
        item.running = false
        handlers[item.id]?.cancel()
        cancelAlarm(item)
        item.endAt = 0L
        persist()
        rebuildRows()
    }

    private fun reset(item: TimerItem) {
        handlers[item.id]?.cancel()
        cancelAlarm(item)
        item.running = false
        item.finished = false
        item.remainingMs = item.durationMs
        item.endAt = 0L
        persist()
        rebuildRows()
    }

    private fun finishTimer(item: TimerItem) {
        handlers[item.id]?.cancel()
        cancelAlarm(item)
        item.running = false
        item.finished = true
        item.remainingMs = 0L
        item.endAt = 0L
        persist()
        rebuildRows()
    }

    private fun delete(item: TimerItem) {
        handlers[item.id]?.cancel()
        cancelAlarm(item)
        timers.remove(item)
        persist()
        rebuildRows()
    }

    private fun currentRemaining(item: TimerItem): Long =
        if (item.running) max(0L, item.endAt - System.currentTimeMillis()) else item.remainingMs

    private fun formatTime(ms: Long): String {
        val total = max(0L, ms) / 1000L
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun persist() {
        val json = JSONArray()
        timers.forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("duration", item.durationMs)
                put("remaining", currentRemaining(item))
                put("endAt", item.endAt)
                put("running", item.running)
                put("finished", item.finished)
            })
        }
        prefs.edit().putString("timers", json.toString()).putInt("nextId", nextId).apply()
    }

    private fun loadTimers() {
        nextId = prefs.getInt("nextId", 1)
        val json = JSONArray(prefs.getString("timers", "[]") ?: "[]")
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            timers.add(TimerItem(
                obj.getInt("id"), obj.optString("title", "Timer"),
                obj.optLong("duration"), obj.optLong("remaining"),
                obj.optLong("endAt"), obj.optBoolean("running"), obj.optBoolean("finished")
            ))
        }
    }

    private fun selectedSoundName(): String {
        val uri = prefs.getString("alarm_sound_uri", null) ?: return "Bawaan perangkat"
        return try {
            contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) ?: "Audio pilihan" else "Audio pilihan"
            } ?: "Audio pilihan"
        } catch (_: Exception) { "Audio pilihan" }
    }

    private fun chooseAlarmSound() {
        AlertDialog.Builder(this)
            .setTitle("Nada timer")
            .setItems(arrayOf("Gunakan nada bawaan perangkat", "Pilih file MP3/WAV")) { _, which ->
                if (which == 0) {
                    prefs.edit().remove("alarm_sound_uri").apply()
                    refreshAlarmChannel()
                    render()
                } else {
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav"))
                    }
                    startActivityForResult(pick, 2101)
                }
            }.show()
    }

    @Deprecated("Deprecated by Android; retained for compatibility with the minimum SDK.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2101 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: ""
                val lower = name.lowercase(Locale.ROOT)
                if (!lower.endsWith(".mp3") && !lower.endsWith(".wav")) {
                    Toast.makeText(this, "Pilih file berformat MP3 atau WAV.", Toast.LENGTH_LONG).show()
                    return
                }
                prefs.edit().putString("alarm_sound_uri", uri.toString()).apply()
                refreshAlarmChannel()
                render()
            } catch (_: Exception) {
                Toast.makeText(this, "File audio tidak dapat diakses.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshAlarmChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.deleteNotificationChannel("timer_done")
            createNotificationChannel()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel("timer_done", "Alarm Timer X", NotificationManager.IMPORTANCE_HIGH)
            val sound = prefs.getString("alarm_sound_uri", null)?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            channel.setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
        }
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
