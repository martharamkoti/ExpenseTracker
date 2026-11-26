package com.ramkotimartha.expensetracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

class ThemeManager(private val context: Context) {
    private val prefs: SharedPreferences? by lazy {
        try {
            context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }
    private val THEME_KEY = "app_theme"

    val currentTheme: AppTheme
        get() {
            val name = prefs?.getString(THEME_KEY, AppTheme.DARK.name) ?: AppTheme.DARK.name
            return try {
                AppTheme.valueOf(name)
            } catch (e: Exception) {
                AppTheme.DARK
            }
        }

    private val _themeFlow = MutableStateFlow(currentTheme)
    val theme: StateFlow<AppTheme> = _themeFlow.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs?.edit()?.putString(THEME_KEY, theme.name)?.apply()
        _themeFlow.value = theme
        applyNightMode(theme)
    }

    private fun applyNightMode(theme: AppTheme) {
        try {
            val nightMode = when (theme) {
                AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            // Ignored in test environment
        }
    }

    companion object {
        fun applyInitialNightMode(context: Context) {
            try {
                val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                val name = prefs.getString("app_theme", AppTheme.DARK.name) ?: AppTheme.DARK.name
                val theme = try {
                    AppTheme.valueOf(name)
                } catch (e: Exception) {
                    AppTheme.DARK
                }
                val nightMode = when (theme) {
                    AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }
}
