package com.example.arcomtechapp

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import androidx.appcompat.app.AppCompatDelegate
import com.example.arcomtechapp.storage.Storage

class ARCoMTechApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyThemePreference()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    private fun applyThemePreference() {
        val storage = Storage(this)
        AppCompatDelegate.setDefaultNightMode(storage.getThemeMode())
    }
}
