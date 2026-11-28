package com.ramkotimartha.expensetracker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists whether the user has completed the one-time first-launch Welcome screen.
 * Synchronously initialized from SharedPreferences so returning users go straight to Home
 * on Frame 1 without any flicker.
 */
class OnboardingManager(private val context: Context) {

    private val prefs: SharedPreferences? by lazy {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    val isCompleted: Boolean
        get() = prefs?.getBoolean(KEY_COMPLETED, false) ?: false

    private val _hasCompletedFlow = MutableStateFlow(isCompleted)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedFlow.asStateFlow()

    fun setCompleted() {
        prefs?.edit()?.putBoolean(KEY_COMPLETED, true)?.apply()
        _hasCompletedFlow.value = true
    }

    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_COMPLETED = "has_completed_onboarding"
    }
}
