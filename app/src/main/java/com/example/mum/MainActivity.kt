package com.example.mum

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.aware.Aware
import com.aware.Aware_Preferences
import com.example.mum.model.DetailItem
import com.example.mum.model.Provider
import com.example.mum.viewHelpers.DetailAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field.FIELD_STEPS
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {


    companion object {
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 10 // for later reference
        const val LOG_TAG = "MUM Fitness History"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Create the connection to the Fitness API
        handleGoogleSignIn()

        // set up the list with the details for the current day
        val dummyList = getActivityList()

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
            if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                updateTodaysStepCount(task.result!!)
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

    private fun getActivityList() : Array<DetailItem> {

        val coveredActivities = mutableListOf<String>()

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val data = contentResolver.query(Provider.Activity_Data.CONTENT_URI, null, Provider.Activity_Data.TIMESTAMP + " >= " + today.timeInMillis, null, Provider.Activity_Data.TIMESTAMP + " DESC")

        val activityList = mutableListOf<DetailItem>()

        if(data != null && data.moveToFirst()) {

            do {
                val type = data.getString(data.getColumnIndex(Provider.Activity_Data.SENSOR_TYPE))
                if (!coveredActivities.contains(type)) {
                    coveredActivities.add(type)
                    val description = getActivityDescription(type)
                    if (!description.isBlank()) {
                        val value = data.getInt(data.getColumnIndex(Provider.Activity_Data.VALUE))
                        val score = data.getInt(data.getColumnIndex(Provider.Activity_Data.SCORE))
                        activityList.add(DetailItem(description, value, score))
                    }

                }

            } while (data.moveToNext())

        }

        return activityList.toTypedArray()

    }


    private fun handleGoogleSignIn() {

        // Check if user is already logged in
        if (GoogleSignIn.getLastSignedInAccount(this) == null) {
            // user is not signed in yet, open the sign in window
            val mGoogleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)
            val signInIntent = mGoogleSignInClient.signInIntent
            startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
        } else {
            // user is already signed in, we can get the step count directly
            updateTodaysStepCount(GoogleSignIn.getLastSignedInAccount(this)!!);
        }
    }

    /**
     * retrieve the device's step count for the current day
     */
    private fun updateTodaysStepCount(googleAccount: GoogleSignInAccount) {

        Fitness.getHistoryClient(this, googleAccount)
            .readDailyTotalFromLocalDevice(DataType.TYPE_STEP_COUNT_DELTA)
            .addOnSuccessListener {
                if (it.isEmpty) {
                    println("step Count not available")
                }
                else {
                    val value = it.dataPoints[0].getValue(FIELD_STEPS).asInt()
                    insertSensorValue("step_count", value, value) // TODO replace second value with score
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

    // TODO move to an enum or something like that
    private fun getActivityDescription(type: String): String {

        return when(type) {
            "social_apps" -> getString(R.string.social_description)
            "step_count" -> getString(R.string.step_counter_description)
            "call_minutes" -> getString(R.string.call_description)
            else -> ""
        }
    }
}
