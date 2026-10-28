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

        // =================================================================
        // SENDER-ONLY CONFIGURATION
        // =================================================================

        // Target Senders (Matches regardless of group or direct chat)
        private val ALLOWED_SENDERS = listOf("Fabeha", "Maryam", "Bhai")

        // Action/Shift Keywords
        private val SHIFT_KEYWORDS = listOf(
            "shift", "shifts", "available", "come in", "pick up", "free",
            "hours", "cover", "covering", "need", "needed", "looking for", "slot"
        )

        // Time Pattern (Matches "1-6", "01:00-06:00", "12to5", "1pm", "14:00")
        private val TIME_REGEX = Regex(
            """\b\d{1,2}(:\d{2})?\s*(am|pm)?\s*(-|to)\s*\d{1,2}(:\d{2})?\s*(am|pm)?\b|\b\d{1,2}\s*(-|to)\s*\d{1,2}\b|\b\d{1,2}(:\d{2})?\s*(am|pm)\b""",
            RegexOption.IGNORE_CASE
        )

        private val DATE_URGENCY_KEYWORDS = listOf(
            "today", "tomorrow", "tonight", "asap", "morning", "afternoon", "evening"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn?.packageName != WHATSAPP_PACKAGE) return

        val extras = sbn.notification.extras ?: return

        // Extract raw notification headers
        val conversationTitle = extras.getString("android.conversationTitle") ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        val rawTitle = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val rawTitleBig = extras.getString(Notification.EXTRA_TITLE_BIG) ?: ""

        // Extract full message body (handles single text & multi-line bundled messages)
        val messageTextList = mutableListOf<String>()
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { messageTextList.add(it) }

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        textLines?.forEach { messageTextList.add(it.toString()) }

        val fullTextCombined = messageTextList.joinToString(" ")

        // Extract sender name and clean text
        val (extractedSender, cleanMessage) = extractSenderAndBody(rawTitle, fullTextCombined)

        // Collect all potential sender fields (Title, Conversation Title, SubText)
        val allHeaderFields = listOf(rawTitle, rawTitleBig, conversationTitle, subText, extractedSender)

        // STEP 1: Verify if ANY header field contains an allowed sender's name
        val matchedSender = ALLOWED_SENDERS.find { targetSender ->
            allHeaderFields.any { header -> header.contains(targetSender, ignoreCase = true) } ||
                    cleanMessage.contains(targetSender, ignoreCase = true)
        }

        if (matchedSender == null) {
            Log.d(TAG, "Ignored: Notification not from target senders (${ALLOWED_SENDERS.joinToString()}). Headers: $allHeaderFields")
            return
        }

        Log.d(TAG, "Notification Received from Sender: '$matchedSender' | Text: '$cleanMessage'")

        // STEP 2: Shift Offer Criteria Check
        val hasShiftKeyword = SHIFT_KEYWORDS.any { cleanMessage.contains(it, ignoreCase = true) }
        val hasTimeOrUrgency = TIME_REGEX.containsMatchIn(cleanMessage) ||
                DATE_URGENCY_KEYWORDS.any { cleanMessage.contains(it, ignoreCase = true) }

        if (!hasShiftKeyword || !hasTimeOrUrgency) {
            Log.d(TAG, "Ignored: Message content from $matchedSender does not meet shift criteria.")
            return
        }

        // STEP 3: Prevent Duplicate Sends
        val notificationKey = "${sbn.key}_$cleanMessage"
        if (notificationKey == lastProcessedKey) {
            Log.d(TAG, "Ignored: Duplicate notification key.")
            return
        }

        // STEP 4: Trigger Direct Reply
        Log.d(TAG, "Valid shift detected from $matchedSender! Responding 'I can'...")
        lastProcessedKey = notificationKey
        sendWhatsAppReply(sbn.notification, "I can")
    }

    private fun extractSenderAndBody(title: String, body: String): Pair<String, String> {
        var sender = title
        var message = body

        // Group chats format messages as "Sender Name: Message text" inside body
        if (body.contains(":")) {
            val parts = body.split(":", limit = 2)
            sender = parts[0].trim()
            message = parts[1].trim()
        }

        return Pair(sender, message)
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
            Log.d(TAG, "Auto-reply 'I can' sent successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Exception while sending inline reply", e)
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