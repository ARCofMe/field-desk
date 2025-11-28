package com.example.arcomtechapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.arcomtechapp.storage.Storage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import androidx.appcompat.app.AppCompatDelegate

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure Python is started even if Application missed for some reason
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val storage = Storage(this)
        AppCompatDelegate.setDefaultNightMode(storage.getThemeMode())
        val apiKey = storage.getApiKey()

        // If no API key is stored, still take the user to the main UI so the drawer is reachable.
        // Settings remains accessible from the nav menu for configuration.
        val next = if (apiKey.isNullOrEmpty()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }

        startActivity(next)

        finish() // do NOT stay in splash
    }
}
