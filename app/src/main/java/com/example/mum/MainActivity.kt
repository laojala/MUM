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
import android.net.Uri
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.example.mum.model.DetailItem
import com.example.mum.viewHelpers.DetailAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field.FIELD_STEPS
import java.util.*
import android.database.Cursor

class MainActivity : AppCompatActivity() {


    companion object {
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 10
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 11 // for later reference
        const val LOG_TAG = "MUM Fitness History"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

       


        // Dummy insert to db
        val values = ContentValues()
        values.put(Provider.Activity_Data.TIMESTAMP, System.currentTimeMillis())
        values.put(Provider.Activity_Data.DEVICE_ID, Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
        values.put(Provider.Activity_Data.SENSOR_TYPE, "social_apps")
        values.put(Provider.Activity_Data.VALUE, 777777.1)
        values.put(Provider.Activity_Data.SCORE, -777777)
        applicationContext.getContentResolver().insert(Provider.Activity_Data.CONTENT_URI, values)
        // Dummy insert to db REMOVE everything from above




        
        // Create the connection to the Fitness API
        handleGoogleSignIn()

        // set up the list with the details for the current day
        val dummyList = arrayOf(DetailItem("steps taken", 23423, 45), DetailItem("minutes on the phone", 12, 12))

        val viewManager = LinearLayoutManager(this)
        val viewAdapter = DetailAdapter(dummyList)

        findViewById<RecyclerView>(R.id.detail_item_list).apply {
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }


    override fun onResume() {
        super.onResume()

        var currentScore = getTodaySocialValue()

        if (currentScore >= 0)
            score.setTextColor(Color.parseColor("#438945"))
        else
            score.setTextColor(Color.parseColor("#E40C2B"))


        score.text=currentScore.toString()


        getTodaySocialValue()
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

    private fun getTodayCursorForActivity(activity: String) : Cursor? {

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val cursor = contentResolver.query(Provider.Activity_Data.CONTENT_URI, null, Provider.Activity_Data.TIMESTAMP + " >= " + today.timeInMillis + " AND " + Provider.Activity_Data.SENSOR_TYPE + " LIKE '${activity}'", null, null)

        return cursor

    }

    private fun getTodaySocialValue() : Int {

        var data = getTodayCursorForActivity("social_apps")
        var totalScore = 0

        if(data != null && data.moveToFirst()) {

            do {
                totalScore += data.getInt(data.getColumnIndex(Provider.Activity_Data.SCORE))
            } while (data.moveToNext())

        }

        return (totalScore /  60000)

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
                    val value = it.dataPoints[0].getValue(FIELD_STEPS).asInt()
                    insertSensorValue("stepCounter", value, value) // TODO replace second value with score
                    println("step Count: ${it.dataPoints[0].getValue(FIELD_STEPS).asInt()}")
                }
            }
            .addOnFailureListener { println("Couldn't retrieve data") }

    }

    private fun insertSensorValue(sensorType: String, value: Int, score: Int): Uri? {
        val values = ContentValues()
        values.put(Provider.Activity_Data.TIMESTAMP, System.currentTimeMillis())
        values.put(Provider.Activity_Data.DEVICE_ID, Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
        values.put(Provider.Activity_Data.SENSOR_TYPE, sensorType)
        values.put(Provider.Activity_Data.VALUE, value)
        values.put(
            Provider.Activity_Data.SCORE,
            score
        ) // for socials can be kept in ms; transferred to mins for ui only
        return applicationContext.contentResolver.insert(Provider.Activity_Data.CONTENT_URI, values)
    }
}
