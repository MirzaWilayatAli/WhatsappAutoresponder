package com.example.whatsappautoresponder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlarmManager.stopAlarm(context)
    }
}