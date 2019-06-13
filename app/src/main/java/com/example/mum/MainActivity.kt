package com.example.mum

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.aware.Applications
import com.aware.Aware
import com.aware.Aware_Preferences
import com.example.mum.model.Provider
import kotlinx.android.synthetic.main.activity_main.*
import android.graphics.Color
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field.FIELD_STEPS

class MainActivity : AppCompatActivity() {

    var opened: Long = 0L
    var closed: Long = 0L
    var time: Long = 0L
    var app_name: String =""

    companion object {
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 10
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 11 // for later reference
        const val LOG_TAG = "MUM Fitness History"
    }

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

        // Create the connection to the Fitness API
        handleGoogleSignIn()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
                getTodaysStepCount(GoogleSignIn.getLastSignedInAccount(this)!!)
            }
            else if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                getTodaysStepCount(task.result!!)
            }
        }
    }

    private fun handleGoogleSignIn() {
        val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA)
            .build()

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            // no account associated with the app yet
            if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                val mGoogleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)
                val signInIntent = mGoogleSignInClient.signInIntent
                startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
            }
            // account for this app already used
            else {
                GoogleSignIn.requestPermissions(
                    this, // your activity
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions)
            }
        } else {
            // user is already signed in, we can get the step count directly
            getTodaysStepCount(GoogleSignIn.getLastSignedInAccount(this)!!);
        }
    }

    /**
     * retrieve the device's step count for the current day
     * TODO: add callback or save to database
     */
    private fun getTodaysStepCount(googleAccount: GoogleSignInAccount) {

        Fitness.getHistoryClient(this, googleAccount)
            .readDailyTotalFromLocalDevice(DataType.TYPE_STEP_COUNT_DELTA)
            .addOnSuccessListener {
                if (it.isEmpty) {
                    println("step Count not available")
                }
                else {
                    println("step Count: ${it.dataPoints[0].getValue(FIELD_STEPS).asInt()}")
                }
            }
            .addOnFailureListener { println("Couldn't retrieve data") }

    }
}
