package com.example.arcomtechapp.runtime

import android.content.Context
import com.example.arcomtechapp.ARCoMTechApp
import com.example.arcomtechapp.data.models.FieldDeskSession
import com.example.arcomtechapp.data.repo.FieldOpsRepository
import com.example.arcomtechapp.data.repo.RepositoryProvider
import com.example.arcomtechapp.storage.Storage

class FieldDeskAppContainer(context: Context) {

    private val appContext = context.applicationContext

    fun storage(): Storage = Storage(appContext)

    fun repository(): FieldOpsRepository = RepositoryProvider.fromStorage(storage())

    fun currentSession(): FieldDeskSession = storage().getFieldDeskSession()
}

fun Context.fieldDeskContainer(): FieldDeskAppContainer =
    (applicationContext as ARCoMTechApp).container
