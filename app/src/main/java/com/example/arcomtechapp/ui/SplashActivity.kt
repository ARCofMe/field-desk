package com.example.arcomtechapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.arcomtechapp.storage.Storage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import androidx.appcompat.app.AppCompatDelegate
import com.example.arcomtechapp.ui.settings.SettingsActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure Python is started even if Application missed for some reason
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val storage = Storage(this)
        AppCompatDelegate.setDefaultNightMode(storage.getThemeMode())
        val configStatus = storage.getConfigStatus()

        val next = if (configStatus.complete) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_REQUIRE_SETUP, true)
            }
        }

        startActivity(next)

        finish() // do NOT stay in splash
    }
}
