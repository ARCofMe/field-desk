package com.example.arcomtechapp.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.example.arcomtechapp.R
import com.example.arcomtechapp.storage.Storage

object AppThemeResolver {
    fun resolveAppTheme(context: Context, storage: Storage): Int {
        return when (storage.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.style.Theme_ARCoMTechApp_Light
            AppCompatDelegate.MODE_NIGHT_YES -> R.style.Theme_ARCoMTechApp_Dark
            else -> {
                val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (mask == Configuration.UI_MODE_NIGHT_YES) {
                    R.style.Theme_ARCoMTechApp_Dark
                } else {
                    R.style.Theme_ARCoMTechApp_Light
                }
            }
        }
    }

    fun resolveSplashTheme(context: Context, storage: Storage): Int {
        return when (storage.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.style.Theme_ARCoMTechApp_Splash_Light
            AppCompatDelegate.MODE_NIGHT_YES -> R.style.Theme_ARCoMTechApp_Splash_Dark
            else -> {
                val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (mask == Configuration.UI_MODE_NIGHT_YES) {
                    R.style.Theme_ARCoMTechApp_Splash_Dark
                } else {
                    R.style.Theme_ARCoMTechApp_Splash_Light
                }
            }
        }
    }
}
