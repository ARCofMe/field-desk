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
import com.example.arcomtechapp.ui.theme.AppThemeResolver
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    FieldDeskNavigator {

    private enum class Destination(
        val menuId: Int?,
        val addToBackStack: Boolean
    ) {
        TODAY(R.id.nav_dashboard, false),
        JOBS(R.id.nav_jobs, false),
        PHOTOS(R.id.nav_photos, false),
        NOTES(R.id.nav_notes, false),
        JOB_DETAIL(null, true),
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var storage: Storage
    private var appliedThemeMode: Int = Int.MIN_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        storage = Storage(this)
        appliedThemeMode = storage.getThemeMode()
        setTheme(AppThemeResolver.resolveAppTheme(this, storage))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            navigateTo(Destination.TODAY)
        }
    }

    override fun onResume() {
        super.onResume()
        if (appliedThemeMode != storage.getThemeMode()) {
            recreate()
            return
        }
        updateNavHeader()
        if (!storage.getConfigStatus().complete) {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_REQUIRE_SETUP, true)
            })
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> openToday()
            R.id.nav_jobs -> openJobs()
            R.id.nav_photos -> openPhotos()
            R.id.nav_notes -> openNotes()
            R.id.nav_settings -> openSettings()
        }

        drawerLayout.closeDrawers()
        return true
    }

    override fun openToday() = navigateTo(Destination.TODAY)

    override fun openJobs() = navigateTo(Destination.JOBS)

    override fun openPhotos() = navigateTo(Destination.PHOTOS)

    override fun openNotes() = navigateTo(Destination.NOTES)

    override fun openJobDetail() = navigateTo(Destination.JOB_DETAIL)

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun navigateTo(destination: Destination) {
        if (!destination.addToBackStack) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, destination.fragment())
            .apply {
                if (destination.addToBackStack) {
                    addToBackStack(destination.name)
                }
            }
            .commit()
        destination.menuId?.let(navView::setCheckedItem)
        drawerLayout.closeDrawers()
    }

    private fun Destination.fragment(): androidx.fragment.app.Fragment = when (this) {
        Destination.TODAY -> TodayFragment()
        Destination.JOBS -> JobsFragment()
        Destination.PHOTOS -> PhotosFragment()
        Destination.NOTES -> NotesFragment()
        Destination.JOB_DETAIL -> JobDetailFragment()
    }

    private fun updateNavHeader() {
        val headerView = navView.getHeaderView(0)
        val title = headerView.findViewById<android.widget.TextView>(R.id.header_title)
        val subtitle = headerView.findViewById<android.widget.TextView>(R.id.header_subtitle)
        val logo = headerView.findViewById<android.widget.ImageView>(R.id.header_logo)
        title.text = storage.getTechnicianName().orEmpty().ifBlank { getString(R.string.fielddesk_nav_title) }
        val activeBaseUrl = storage.getActiveBaseUrl()
        subtitle.text = activeBaseUrl?.takeIf { it.isNotBlank() } ?: getString(R.string.fielddesk_setup_required)
        logo.setImageResource(R.drawable.arcom_logo_full)
    }
}
