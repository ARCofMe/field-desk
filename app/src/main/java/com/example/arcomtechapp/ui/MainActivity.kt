package com.example.arcomtechapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.activity.OnBackPressedCallback
import androidx.drawerlayout.widget.DrawerLayout
import com.example.arcomtechapp.R
import com.example.arcomtechapp.storage.Storage
import com.example.arcomtechapp.ui.settings.SettingsActivity
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var storage: Storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = Storage(this)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)
        updateNavHeader()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
                    drawerLayout.closeDrawer(android.view.Gravity.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        if (savedInstanceState == null) {
            showFragment(TodayFragment())
            navView.setCheckedItem(R.id.nav_dashboard)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard ->
                showFragment(TodayFragment())
            R.id.nav_jobs ->
                showFragment(JobsFragment())
            R.id.nav_photos ->
                showFragment(PhotosFragment())
            R.id.nav_notes ->
                showFragment(NotesFragment())
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        drawerLayout.closeDrawers()
        return true
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }

    private fun updateNavHeader() {
        val headerView = navView.getHeaderView(0)
        val title = headerView.findViewById<android.widget.TextView>(R.id.header_title)
        val subtitle = headerView.findViewById<android.widget.TextView>(R.id.header_subtitle)
        val logo = headerView.findViewById<android.widget.ImageView>(R.id.header_logo)
        title.text = storage.getTechnicianName().orEmpty().ifBlank { "ARCoM Tech" }
        val baseUrl = storage.getBaseUrl()
        subtitle.text = baseUrl?.takeIf { it.isNotBlank() } ?: "BlueFolder not configured"
        logo.setImageResource(R.drawable.arcom_logo_full)
    }
}
