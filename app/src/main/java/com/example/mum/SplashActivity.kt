package com.example.mum

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v4.content.PermissionChecker
import android.util.Log
import com.aware.Applications
import com.aware.Aware
import com.aware.Aware_Preferences
import com.aware.ui.PermissionsHandler
import com.example.mum.model.Provider
import java.util.ArrayList

class SplashActivity : AppCompatActivity() {

    var opened: Long = 0L
    var closed: Long = 0L
    var time: Long = 0L
    var app_name: String =""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up Aware
        Aware.startAWARE(applicationContext)
        Aware.setSetting(applicationContext, Aware_Preferences.STATUS_APPLICATIONS, true)

        // Track social app use
        Applications.isAccessibilityServiceActive(applicationContext)
        Applications.setSensorObserver(object : Applications.AWARESensorObserver {
            override fun onCrash(data: ContentValues?) {
            }

            override fun onNotification(data: ContentValues?) {
            }

            override fun onBackground(data: ContentValues?) {
            }

            override fun onKeyboard(data: ContentValues?) {
            }

            override fun onTouch(data: ContentValues?) {
            }

            override fun onForeground(data: ContentValues?) {

                if (opened != 0L) {
                    // calculate time
                    closed = data!!.getAsLong("timestamp")
                    time = closed - opened

                    // insert data to db
                    Log.d("MUMTAG", "$app_name was used for $time ms")
                    val values = ContentValues()
                    values.put(Provider.Activity_Data.TIMESTAMP, System.currentTimeMillis())
                    values.put(Provider.Activity_Data.DEVICE_ID, Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
                    values.put(Provider.Activity_Data.SENSOR_TYPE, "social_apps")
                    values.put(Provider.Activity_Data.VALUE, time)
                    values.put(Provider.Activity_Data.SCORE, -time.div(60000)) // for socials can be kept in ms; transferred to mins for ui only
                    applicationContext.getContentResolver().insert(Provider.Activity_Data.CONTENT_URI, values)

                    // clear values for next turn
                    opened = 0L;
                    closed = 0L;
                    time = 0L;

                } else {
                    //check opened app package name
                    app_name = data!!.getAsString("package_name")

                    if (app_name.equals("com.instagram.android") ||
                        app_name.equals("com.google.android.youtube")) {
                        opened = data.getAsLong("timestamp")
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        // List of required permission
        val REQUIRED_PERMISSIONS = ArrayList<String>()
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_SMS)
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CALL_LOG)
        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        REQUIRED_PERMISSIONS.add(Manifest.permission.GET_ACCOUNTS)

        // flag to check permissions
        var permissions_ok = true
        for (p in REQUIRED_PERMISSIONS) {
            if (PermissionChecker.checkSelfPermission(this, p) != PermissionChecker.PERMISSION_GRANTED) {
                permissions_ok = false
                break
            }
        }

        // Open MainActivity when all conditions are ok
        if (permissions_ok) {
            val main = Intent(this, MainActivity::class.java)
            startActivity(main)
            finish()

        } else {

            val permissions = Intent(this, PermissionsHandler::class.java)
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS)
            permissions.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(permissions)
        }
    }
}
