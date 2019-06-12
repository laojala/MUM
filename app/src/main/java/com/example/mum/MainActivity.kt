package com.example.mum

import android.content.ContentValues
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.aware.Applications
import com.aware.Aware
import com.aware.Aware_Preferences
import com.example.mum.model.Provider
import kotlinx.android.synthetic.main.activity_main.*
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    var opened: Long = 0L
    var closed: Long = 0L
    var time: Long = 0L
    var app_name: String =""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Aware.startAWARE(applicationContext)
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
                    values.put(Provider.Activity_Data.VALUE, time)
                    values.put(Provider.Activity_Data.SCORE, time) // for socials can be kept in ms; transferred to mins for ui only
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

        var currentScore = 23

        if (currentScore >= 0)
            score.setTextColor(Color.parseColor("#438945"))
        else
            score.setTextColor(Color.parseColor("#E40C2B"))


        score.text=currentScore.toString()


    }
}
