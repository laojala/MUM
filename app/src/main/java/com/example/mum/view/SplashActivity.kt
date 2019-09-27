package com.example.mum.view

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.support.v4.content.PermissionChecker
import android.util.Log
import com.aware.Applications
import com.aware.Aware
import com.aware.Aware_Preferences
import com.aware.Communication
import com.aware.ui.PermissionsHandler
import com.example.mum.model.Provider
import java.util.ArrayList

class SplashActivity : AppCompatActivity() {

    var opened: Long = 0L
    var closed: Long = 0L
    var time: Long = 0L
    var app_name: String =""

    override fun onResume() {
        super.onResume()

        // List of required permission
        val REQUIRED_PERMISSIONS = ArrayList<String>()
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_SMS)
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CALL_LOG)
        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        REQUIRED_PERMISSIONS.add(Manifest.permission.GET_ACCOUNTS)
        REQUIRED_PERMISSIONS.add(Manifest.permission.CALL_PHONE)

        // flag to check permissions
        var permissions_ok = true
        for (p in REQUIRED_PERMISSIONS) {
            if (PermissionChecker.checkSelfPermission(this, p) != PermissionChecker.PERMISSION_GRANTED) {
                permissions_ok = false
                break
            }
        }

        // If permissions are ok, do stuff
        if (permissions_ok) {

            // Set up Aware
            Aware.startAWARE(applicationContext)
            Aware.setSetting(applicationContext, Aware_Preferences.DEBUG_FLAG, true)

            // Track social app use
            Aware.setSetting(applicationContext, Aware_Preferences.STATUS_APPLICATIONS, true)
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
                        values.put(Provider.Activity_Data.VALUE, time)  //raw value in ms
                        values.put(Provider.Activity_Data.SCORE, -time.div(60000)) //score in minutes
                        applicationContext.getContentResolver().insert(Provider.Activity_Data.CONTENT_URI, values)

                        // clear values for next turn
                        opened = 0L;
                        closed = 0L;
                        time = 0L;

                    } else {
                        //check opened app package name
                        app_name = data!!.getAsString("package_name")

                        if (app_name.equals("com.instagram.android") ||
                            app_name.equals("com.google.android.youtube") ||
                            app_name.equals("com.whatsapp") ||
                            app_name.equals("com.facebook.katana") ||
                            app_name.equals("com.facebook.orca")) {
                            opened = data.getAsLong("timestamp")
                        }
                    }
                }
            })

            // Track phone calls
            Aware.setSetting(applicationContext, Aware_Preferences.STATUS_COMMUNICATION_EVENTS, true)
            Aware.setSetting(applicationContext, Aware_Preferences.STATUS_CALLS, true)
            Aware.startCommunication(this)
            Communication.setSensorObserver(object : Communication.AWARESensorObserver {
                override fun onCall(data: ContentValues?) {
                    val duration: Int = data!!.getAsInteger("call_duration")
                    Log.d("MUMTAG", "Call was made for $duration seconds")

                    if (duration > 0) {
                        // insert data to db
                        val values = ContentValues()
                        values.put(Provider.Activity_Data.TIMESTAMP, System.currentTimeMillis())
                        values.put(Provider.Activity_Data.DEVICE_ID, Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
                        values.put(Provider.Activity_Data.SENSOR_TYPE, "call_minutes")
                        values.put(Provider.Activity_Data.VALUE, duration) //raw in secs
                        val minutes: Double = Math.ceil(duration.toDouble()/60)
                        val score : Int = minutes.toInt()*3
                        values.put(Provider.Activity_Data.SCORE, score) // score in minutes
                        applicationContext.getContentResolver().insert(Provider.Activity_Data.CONTENT_URI, values)
                    }
                }

                override fun onMessage(data: ContentValues?) {
                }

                override fun onBusy(number: String?) {
                }

                override fun onRinging(number: String?) {
                }

                override fun onFree(number: String?) {
                }
            })

            // Check if the app is opened first time
            val pref = (applicationContext).getSharedPreferences("instructions", Context.MODE_PRIVATE)
            if (!pref.getBoolean("hidden", false)) {
                // If yes, open the instructions screen
                val instructions = Intent(this, InstructionsActivity::class.java)
                startActivity(instructions)
                finish()
            } else {
                // If no, open main activity
                val main = Intent(this, MainActivity::class.java)
                startActivity(main)
                finish()
            }

        } else {
            //Request permissions
            val permissions = Intent(this, PermissionsHandler::class.java)
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS)
            permissions.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(permissions)
        }
    }
}
