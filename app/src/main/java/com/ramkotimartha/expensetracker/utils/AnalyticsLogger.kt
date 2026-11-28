package com.ramkotimartha.expensetracker.utils

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent


object AnalyticsLogger {

    /**
     * Logs a custom event to Firebase Analytics.
     *
     * @param eventName The name of the event to log.
     * @param params An optional bundle of parameters to associate with the event.
     */
    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            if (params != null) {
                Firebase.analytics.logEvent(eventName, params)
            } else {
                Firebase.analytics.logEvent(eventName, null)
            }
        } catch (_: Exception) {
            // Ignored in local unit test environments where Firebase is not initialized
        }
    }

    /**
     * Logs a screen view event with a dynamic event name.
     */
    fun logScreenView(screenName: String) {
        try {
            Firebase.analytics.logEvent("screen_view_${screenName}", null)
        } catch (_: Exception) {
            // Ignored in local unit test environments where Firebase is not initialized
        }
    }
}
