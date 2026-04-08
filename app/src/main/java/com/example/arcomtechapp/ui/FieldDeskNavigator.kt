package com.example.arcomtechapp.ui

import androidx.fragment.app.Fragment

interface FieldDeskNavigator {
    fun openToday()
    fun openJobs()
    fun openPhotos()
    fun openNotes()
    fun openJobDetail()
    fun openSettings()
}

fun Fragment.fieldDeskNavigator(): FieldDeskNavigator =
    requireActivity() as FieldDeskNavigator
