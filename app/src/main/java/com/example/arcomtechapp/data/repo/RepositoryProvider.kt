package com.example.arcomtechapp.data.repo

import android.content.Context
import com.example.arcomtechapp.storage.Storage

object RepositoryProvider {

    fun fromContext(context: Context): FieldOpsRepository {
        val storage = Storage(context.applicationContext)
        return fromStorage(storage)
    }

    fun fromStorage(storage: Storage): FieldOpsRepository {
        return when (storage.getBackendMode()) {
            Storage.BackendMode.OPS_HUB -> OpsHubFieldOpsRepository()
            Storage.BackendMode.BLUEFOLDER_DIRECT -> BlueFolderFieldOpsRepository()
        }
    }
}
