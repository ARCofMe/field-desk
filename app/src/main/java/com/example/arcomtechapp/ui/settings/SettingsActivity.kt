package com.example.arcomtechapp.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import com.example.arcomtechapp.R
import com.example.arcomtechapp.data.repo.BlueFolderFieldOpsRepository
import com.example.arcomtechapp.data.repo.OpsHubFieldOpsRepository
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.databinding.ActivitySettingsBinding
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.ui.theme.AppThemeResolver
import com.google.android.material.appbar.MaterialToolbar
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var storage: Storage
    private var suppressThemeToggle = false
    private var suppressBackendToggle = false
    private var appliedThemeMode: Int = Int.MIN_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        storage = Storage(this)
        appliedThemeMode = storage.getThemeMode()
        setTheme(AppThemeResolver.resolveAppTheme(this, storage))
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: MaterialToolbar = binding.settingsToolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        loadSettings()
        setupListeners()

        if (intent.getBooleanExtra(EXTRA_REQUIRE_SETUP, false)) {
            val missing = storage.getConfigStatus().missingItems
            binding.textTestResult.text = buildString {
                append("Complete setup before using the app.")
                if (missing.isNotEmpty()) {
                    append("\nMissing: ")
                    append(missing.joinToString(", "))
                }
            }
        }
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

        binding.toggleTheme.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked || suppressThemeToggle) return@addOnButtonCheckedListener
            binding.textTestResult.text = "Theme selection changed. Tap Save settings, then relaunch the app."
        }

        binding.buttonManualSync.setOnClickListener {
            storage.markSyncNow()
            loadSettings()
        }

        binding.toggleBackend.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressBackendToggle) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                binding.buttonBackendOpshub.id -> Storage.BackendMode.OPS_HUB
                else -> Storage.BackendMode.BLUEFOLDER_DIRECT
            }
            storage.setBackendMode(mode)
            updateBackendVisibility(mode)
            updateStatus()
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
        val previousThemeMode = storage.getThemeMode()
        storage.saveApiKey(binding.inputApiKey.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.saveBaseUrl(binding.inputBaseUrl.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.saveOpsHubBaseUrl(binding.inputOpsHubBaseUrl.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.saveOpsHubApiKey(binding.inputOpsHubApiKey.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.saveFieldDeskWebUrl(binding.inputFielddeskWebUrl.text?.toString()?.trim().orEmpty().ifBlank { null })
        storage.setPreferWebFieldDesk(binding.switchPreferWebFielddesk.isChecked)
        storage.setThemeMode(selectedThemeMode())
        storage.saveTechnician(
            binding.inputTechnicianName.text?.toString()?.trim().orEmpty().ifBlank { null },
            binding.inputTechnicianId.text?.toString()?.trim().orEmpty().ifBlank { null }
        )
        // Switches already persist immediately.
        updateStatus()
        if (previousThemeMode != selectedThemeMode()) {
            binding.textTestResult.text = "Settings saved. Applying FieldDesk appearance..."
            recreate()
            return
        }
        binding.textTestResult.text = "Settings saved."
    }

    private fun loadSettings() {
        val snapshot = storage.getSnapshot()

        binding.inputApiKey.setText(snapshot.apiKey.orEmpty())
        binding.inputBaseUrl.setText(snapshot.baseUrl.orEmpty())
        binding.inputOpsHubBaseUrl.setText(snapshot.opsHubBaseUrl.orEmpty())
        binding.inputOpsHubApiKey.setText(snapshot.opsHubApiKey.orEmpty())
        binding.inputFielddeskWebUrl.setText(snapshot.fieldDeskWebUrl.orEmpty())
        binding.inputTechnicianName.setText(snapshot.technicianName.orEmpty())
        binding.inputTechnicianId.setText(snapshot.technicianId.orEmpty())

        binding.switchAuthenticated.isChecked = snapshot.isAuthenticated
        binding.switchAutoCompress.isChecked = snapshot.autoCompressPhotos
        binding.switchDebugMode.isChecked = snapshot.debugMode
        binding.switchPreferWebFielddesk.isChecked = snapshot.preferWebFieldDesk

        binding.textLastSync.text = formatSyncTime(snapshot.lastSyncEpochMillis)
        updateStatus(snapshot)
        updateThemeToggle(snapshot.themeMode)
        updateBackendToggle(snapshot.backendMode)
        updateBackendVisibility(snapshot.backendMode)
    }

    private fun updateStatus(snapshot: Storage.SettingsSnapshot = storage.getSnapshot()) {
        val pieces = mutableListOf<String>()
        pieces.add(
            when (snapshot.backendMode) {
                Storage.BackendMode.OPS_HUB -> "Ops Hub backend"
                Storage.BackendMode.BLUEFOLDER_DIRECT -> "BlueFolder direct"
            }
        )
        val activeBase = if (snapshot.backendMode == Storage.BackendMode.OPS_HUB) snapshot.opsHubBaseUrl else snapshot.baseUrl
        val activeKey = if (snapshot.backendMode == Storage.BackendMode.OPS_HUB) snapshot.opsHubApiKey else snapshot.apiKey
        if (activeKey.isNullOrBlank()) pieces.add("API key missing") else pieces.add("API key saved")
        if (activeBase.isNullOrBlank()) pieces.add("Base URL missing") else pieces.add("Base URL set")
        if (snapshot.technicianId.isNullOrBlank()) pieces.add("Technician ID missing") else pieces.add("Technician ID set")
        if (snapshot.backendMode == Storage.BackendMode.OPS_HUB && snapshot.preferWebFieldDesk) {
            if (snapshot.fieldDeskWebUrl.isNullOrBlank()) pieces.add("Web host URL missing") else pieces.add("Web host URL set")
        }
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

    private fun selectedThemeMode(): Int {
        return when (binding.toggleTheme.checkedButtonId) {
            binding.buttonThemeLight.id -> AppCompatDelegate.MODE_NIGHT_NO
            binding.buttonThemeDark.id -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    private fun updateBackendToggle(mode: Storage.BackendMode) {
        val buttonId = when (mode) {
            Storage.BackendMode.OPS_HUB -> binding.buttonBackendOpshub.id
            Storage.BackendMode.BLUEFOLDER_DIRECT -> binding.buttonBackendBluefolder.id
        }
        suppressBackendToggle = true
        binding.toggleBackend.check(buttonId)
        suppressBackendToggle = false
    }

    private fun updateBackendVisibility(mode: Storage.BackendMode) {
        binding.layoutBluefolderConfig.visibility =
            if (mode == Storage.BackendMode.BLUEFOLDER_DIRECT) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutOpsHubConfig.visibility =
            if (mode == Storage.BackendMode.OPS_HUB) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun testConnection() {
        val mode = storage.getBackendMode()
        val baseUrl = when (mode) {
            Storage.BackendMode.OPS_HUB -> binding.inputOpsHubBaseUrl.text?.toString()?.trim()
            Storage.BackendMode.BLUEFOLDER_DIRECT -> binding.inputBaseUrl.text?.toString()?.trim()
        }
        val apiKey = when (mode) {
            Storage.BackendMode.OPS_HUB -> binding.inputOpsHubApiKey.text?.toString()?.trim()
            Storage.BackendMode.BLUEFOLDER_DIRECT -> binding.inputApiKey.text?.toString()?.trim()
        }
        val repo: FieldOpsRepository = when (mode) {
            Storage.BackendMode.OPS_HUB -> OpsHubFieldOpsRepository()
            Storage.BackendMode.BLUEFOLDER_DIRECT -> BlueFolderFieldOpsRepository()
        }
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
    companion object {
        const val EXTRA_REQUIRE_SETUP = "require_setup"
    }
}
