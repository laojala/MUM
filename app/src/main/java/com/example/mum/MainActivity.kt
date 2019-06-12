package com.example.mum

import android.content.ContentValues
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.aware.Aware
import com.aware.Aware_Preferences
import com.example.mum.model.Provider

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Dummy insert to db
        val values = ContentValues()
        values.put(Provider.Activity_Data.TIMESTAMP, System.currentTimeMillis())
        values.put(Provider.Activity_Data.DEVICE_ID, Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
        values.put(Provider.Activity_Data.SENSOR_TYPE, "step_counter")
        values.put(Provider.Activity_Data.VALUE, 3456)
        values.put(Provider.Activity_Data.SCORE, 123)
        applicationContext.getContentResolver().insert(Provider.Activity_Data.CONTENT_URI, values)
    }
}
