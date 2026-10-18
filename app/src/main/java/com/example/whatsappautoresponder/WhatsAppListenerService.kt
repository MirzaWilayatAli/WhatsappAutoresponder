package com.example.whatsappautoresponder

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WhatsAppListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppAutoResponder"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private var lastProcessedKey: String = ""

        // --- CONFIGURATION RULES ---
        private const val TARGET_GROUP = "TK Maxx Fountain Gate"

        // Senders who post shifts
        private val ALLOWED_SENDERS = listOf(
            "Mike Somervell", "Emily Mulholland", "Lacey", "Jessica", "Tina"
        )

        // Expanded shift keywords & role terms
        private val SHIFT_KEYWORDS = listOf(
            "shift", "available", "come in", "pick up", "free", "hours",
            "cover", "after", "maintenance", "fitting room", "flow", "dtf",
            "health and beauty", "accessories"
        )

        // EXCLUSION: Register terms
        private val REGISTER_EXCLUSIONS = listOf("register", "reg", "till", "checkout")

        // Specific non-register roles to target when present alongside register mentions
        private val VALID_DEPARTMENTS = listOf(
            "maintenance", "fitting room", "flow", "dtf", "health and beauty", "accessories"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn?.packageName != WHATSAPP_PACKAGE) return

        val extras = sbn.notification.extras

        val groupName = extras.getString("android.support.notification.conversationTitle")
            ?: extras.getString(Notification.EXTRA_SUB_TEXT)
            ?: extras.getString(Notification.EXTRA_TITLE) ?: ""

        val senderName = extras.getString(Notification.EXTRA_TITLE) ?: "Unknown"
        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        Log.d(TAG, "Notification Received -> Group: [$groupName] | Sender: [$senderName] | Text: [$messageText]")

        // 1. Ensure it's from our target group
        if (!groupName.contains(TARGET_GROUP, ignoreCase = true)) {
            Log.d(TAG, "Ignored: Group name did not match target group.")
            return
        }

        // 2. Check if the sender is in our allowed list
        val isAllowedSender = ALLOWED_SENDERS.any { senderName.contains(it, ignoreCase = true) }
        if (!isAllowedSender) {
            Log.d(TAG, "Ignored: Sender '$senderName' is not in the allowed list.")
            return
        }

        // 3. Check for shift indicators (keywords or time formats like 1:30-6:30 or 10-6:30)
        val hasShiftKeyword = SHIFT_KEYWORDS.any { messageText.contains(it, ignoreCase = true) }
        val hasTimePattern = messageText.contains(
            Regex("\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\s*(-|to)\\s*\\d{1,2}(:\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        )

        if (!hasShiftKeyword && !hasTimePattern) {
            Log.d(TAG, "Ignored: Message does not look like a shift offer.")
            return
        }

        // 4. Handle Register vs Non-Register shifts
        val mentionsRegister = REGISTER_EXCLUSIONS.any { messageText.contains(it, ignoreCase = true) }
        val matchedDepartment = VALID_DEPARTMENTS.find { messageText.contains(it, ignoreCase = true) }

        // If it ONLY mentions a register shift and NO other valid department, skip it
        if (mentionsRegister && matchedDepartment == null) {
            Log.d(TAG, "Ignored: Strictly a register shift.")
            return
        }

        // 5. Determine the tailored reply text
        val autoReplyText = when {
            matchedDepartment != null && mentionsRegister -> "I can do $matchedDepartment"
            matchedDepartment != null -> "I can do $matchedDepartment"
            else -> "I can"
        }

        // 6. Prevent loops/duplicates
        val notificationKey = "${sbn.key}_$messageText"
        if (notificationKey == lastProcessedKey) {
            Log.d(TAG, "Ignored: Duplicate notification key.")
            return
        }

        // All conditions met! Send reply
        Log.d(TAG, "Valid shift found! Sending reply '$autoReplyText'...")
        lastProcessedKey = notificationKey
        sendWhatsAppReply(sbn.notification, autoReplyText)
    }

    private fun sendWhatsAppReply(notification: Notification, replyText: String) {
        val action = findReplyAction(notification) ?: return
        val remoteInputs = action.remoteInputs ?: return

        try {
            val intent = Intent()
            val bundle = Bundle()

            for (input in remoteInputs) {
                bundle.putCharSequence(input.resultKey, replyText)
                RemoteInput.addResultsToIntent(arrayOf(input), intent, bundle)
            }

            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "Shift auto-reply sent successfully!")

            // --- START LOUD ALARM TO WAKE UP ---
            AlarmManager.startAlarm(this)

        } catch (e: Exception) {
            Log.e(TAG, "Exception while sending reply", e)
            e.printStackTrace()
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            if (action.remoteInputs != null) {
                for (input in action.remoteInputs) {
                    if (input.resultKey != null) {
                        return action
                    }
                }
            }
        }
        return null
    }
}