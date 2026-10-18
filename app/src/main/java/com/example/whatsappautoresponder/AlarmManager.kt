package com.example.whatsappautoresponder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object AlarmManager {
    private var mediaPlayer: MediaPlayer? = null
    private const val CHANNEL_ID = "shift_alarm_channel"
    private const val NOTIFICATION_ID = 999

    // ADDED HERE: Suppresses the POST_NOTIFICATIONS permission warning
    @SuppressLint("MissingPermission")
    fun startAlarm(context: Context) {
        if (mediaPlayer?.isPlaying == true) return

        try {
            // Create notification channel for Android 8+
            createNotificationChannel(context)

            // Create an intent that stops the alarm when the notification button is clicked
            val stopIntent = Intent(context, StopAlarmReceiver::class.java)
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build a persistent notification with a Stop button
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🚨 Shift Auto-Claimed!")
                .setContentText("Tap below to stop the loud alarm ringtone.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true) // Cannot be swiped away easily until stopped
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ALARM", stopPendingIntent)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)

            // Play the ringtone in a loop
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlarm(context: Context) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            // Remove the persistent notification when stopped
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shift Alarm Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when a shift is auto-claimed"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}