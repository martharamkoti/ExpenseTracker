package com.ramkotimartha.expensetracker.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashlyticsLogger {

    /**
     * Logs a non-fatal exception to Firebase Crashlytics.
     *
     * @param throwable The exception to log.
     * @param message An optional descriptive message.
     */
    fun log(throwable: Throwable, message: String? = null) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (message != null) {
            crashlytics.log(message)
        }
        crashlytics.recordException(throwable)
    }
    
    /**
     * Logs a simple message to Crashlytics to add context to future reports.
     */
    fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }
}
