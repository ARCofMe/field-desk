package com.example.arcomtechapp.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.repo.BlueFolderRepository
import com.example.arcomtechapp.databinding.ActivitySettingsBinding
import com.example.arcomtechapp.storage.Storage
import com.google.android.material.appbar.MaterialToolbar
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var storage: Storage
    private val repo = BlueFolderRepository()
    private var suppressThemeToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = Storage(this)

        val toolbar: MaterialToolbar = binding.settingsToolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        loadSettings()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupListeners() {
        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.buttonTestConnection.setOnClickListener {
            testConnection()
        }

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressThemeToggle) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                binding.buttonThemeLight.id -> AppCompatDelegate.MODE_NIGHT_NO
                binding.buttonThemeDark.id -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            storage.setThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        binding.buttonManualSync.setOnClickListener {
            storage.markSyncNow()
            loadSettings()
        }

        binding.switchAuthenticated.setOnCheckedChangeListener { _, isChecked ->
            storage.setAuthenticated(isChecked)
            updateStatus()
        }

        binding.switchAutoCompress.setOnCheckedChangeListener { _, isChecked ->
            storage.setAutoCompressPhotos(isChecked)
        }

        binding.switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            storage.setDebugMode(isChecked)
        }
    }

    private fun saveSettings() {
        storage.saveApiKey(binding.inputApiKey.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.saveBaseUrl(binding.inputBaseUrl.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.saveTechnician(
            binding.inputTechnicianName.text?.toString()?.trim().orEmpty().ifBlank { null },
            binding.inputTechnicianId.text?.toString()?.trim().orEmpty().ifBlank { null }
        )
        // Switches already persist immediately.
        updateStatus()
    }

    private fun loadSettings() {
        val snapshot = storage.getSnapshot()

        binding.inputApiKey.setText(snapshot.apiKey.orEmpty())
        binding.inputBaseUrl.setText(snapshot.baseUrl.orEmpty())
        binding.inputTechnicianName.setText(snapshot.technicianName.orEmpty())
        binding.inputTechnicianId.setText(snapshot.technicianId.orEmpty())

        binding.switchAuthenticated.isChecked = snapshot.isAuthenticated
        binding.switchAutoCompress.isChecked = snapshot.autoCompressPhotos
        binding.switchDebugMode.isChecked = snapshot.debugMode

        binding.textLastSync.text = formatSyncTime(snapshot.lastSyncEpochMillis)
        updateStatus(snapshot)
        updateThemeToggle(snapshot.themeMode)
    }

    private fun updateStatus(snapshot: Storage.SettingsSnapshot = storage.getSnapshot()) {
        val pieces = mutableListOf<String>()
        if (snapshot.apiKey.isNullOrBlank()) pieces.add("API key missing") else pieces.add("API key saved")
        if (snapshot.baseUrl.isNullOrBlank()) pieces.add("Subdomain missing") else pieces.add("Subdomain set")
        pieces.add(if (snapshot.isAuthenticated) "Authenticated" else "Not authenticated")
        binding.textStatus.text = pieces.joinToString(" • ")
    }

    private fun formatSyncTime(epochMillis: Long): String {
        if (epochMillis == 0L) return "Last sync: Never"
        val formatted = DateFormat.getDateTimeInstance().format(Date(epochMillis))
        return "Last sync: $formatted"
    }

    private fun updateThemeToggle(mode: Int) {
        val buttonId = when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.buttonThemeLight.id
            AppCompatDelegate.MODE_NIGHT_YES -> binding.buttonThemeDark.id
            else -> binding.buttonThemeSystem.id
        }
        suppressThemeToggle = true
        binding.toggleTheme.check(buttonId)
        suppressThemeToggle = false
    }

    private fun testConnection() {
        val baseUrl = binding.inputBaseUrl.text?.toString()?.trim()
        val apiKey = binding.inputApiKey.text?.toString()?.trim()
        binding.textTestResult.text = "Testing..."
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                repo.checkConnection(baseUrl, apiKey)
            } catch (e: Exception) {
                "Test failed: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                binding.textTestResult.text = result
            }
        }
    }
}
