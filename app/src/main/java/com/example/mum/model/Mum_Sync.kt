package com.example.mum.model

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder

import com.aware.syncadapters.AwareSyncAdapter

class Mum_Sync : Service() {
    private var sSyncAdapter: AwareSyncAdapter? = null

    override fun onCreate() {
        super.onCreate()
        synchronized(sSyncAdapterLock) {

            if (sSyncAdapter == null) {
                sSyncAdapter = AwareSyncAdapter(applicationContext, true, true)
                sSyncAdapter!!.init(
                    Provider.DATABASE_TABLES, Provider.TABLES_FIELDS,
                    arrayOf(Provider.Activity_Data.CONTENT_URI)
                )
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return sSyncAdapter!!.syncAdapterBinder
    }

    companion object {
        private val sSyncAdapterLock = Any()
    }
}

