package com.example.arcomtechapp

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.arcomtechapp.runtime.FieldDeskAppContainer

class ARCoMTechApp : Application() {
    lateinit var container: FieldDeskAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = FieldDeskAppContainer(this)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
