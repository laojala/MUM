package com.example.mum

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import com.aware.Aware
import com.aware.Aware_Preferences
import com.example.mum.model.DetailItem
import com.example.mum.model.Provider
import com.example.mum.viewHelpers.DetailAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field.FIELD_STEPS
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var detailAdapter: DetailAdapter

    companion object {
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 10 // for later reference
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item!!.getItemId()

        if (id == R.id.menu_instructions) {
            val instructions = Intent(this, InstructionsActivity::class.java)
            startActivity(instructions)
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Create the connection to the Fitness API
        handleGoogleSignIn()

        // set up the list with the details for the current day
        val dummyList = getActivityList()

        val viewManager = LinearLayoutManager(this)
        detailAdapter = DetailAdapter(dummyList.values.toTypedArray())

        findViewById<RecyclerView>(R.id.detail_item_list).apply {
            layoutManager = viewManager
            adapter = detailAdapter
        }
    }


    override fun onResume() {
        super.onResume()

        val currentScore = getActivityList().values.sumBy { it.score }


        // Colour the daily balance depending on its value
        if (currentScore >= 0) {
            // good score - let's colour it green
            score.setTextColor(ContextCompat.getColor(this, R.color.positiveColor))
            scoreTitle.text = "Great job today!"
            scoreText.text = ""
        }
        else {
            // negative score - let's colour it red
            score.setTextColor(ContextCompat.getColor(this, R.color.negativeColor))
            scoreTitle.text = "Oops. Negative balance"
            scoreText.text = "Maybe try to have a walk"
        }

        score.text = currentScore.toString()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                task.addOnSuccessListener {
                    updateTodaysStepCount(task.result!!)
                }
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

        return totalScore

    }

    /**
     * returns the current day activities including description, score, and value from the database
     */
    private fun getActivityList() : HashMap<String, DetailItem> {

        val activityItems = hashMapOf<String, DetailItem>()

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val data = contentResolver.query(Provider.Activity_Data.CONTENT_URI, null, Provider.Activity_Data.TIMESTAMP + " >= " + today.timeInMillis, null, Provider.Activity_Data.TIMESTAMP + " DESC")

        if(data != null && data.moveToFirst()) {

            do {
                val type = data.getString(data.getColumnIndex(Provider.Activity_Data.SENSOR_TYPE))
                if (!activityItems.containsKey(type)) {
                    val description = getActivityDescription(type)
                    if (!description.isBlank()) {
                        val value = data.getInt(data.getColumnIndex(Provider.Activity_Data.VALUE))
                        val score = data.getInt(data.getColumnIndex(Provider.Activity_Data.SCORE))
                        activityItems[type] = DetailItem(description, value, score)
                    }
                }
                else if (type == "social_apps" && activityItems.containsKey(type)) {
                    val value = data.getInt(data.getColumnIndex(Provider.Activity_Data.VALUE))
                    val score = data.getInt(data.getColumnIndex(Provider.Activity_Data.SCORE))
                    activityItems[type]!!.score += score
                    activityItems[type]!!.value += value
                }

            } while (data.moveToNext())

        }

        // the social app time is stored in milliseconds, but for the display, we want it in minutes
        // We are also rounding up to 1 minute if the user was spending less time than that on the social apps
        activityItems["social_apps"]?.value = Math.max((activityItems["social_apps"]!!.value / 60000.toDouble()).roundToInt(), 1)
        activityItems["social_apps"]?.score = activityItems["social_apps"]!!.value * (-1)

        return activityItems

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
            updateTodaysStepCount(GoogleSignIn.getLastSignedInAccount(this)!!)
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
                    insertSensorValue("step_count", value, (value / 100.toDouble()).roundToInt())
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

        val uri = applicationContext.contentResolver.insert(Provider.Activity_Data.CONTENT_URI, values)

        // update the view
        // includes reading from the database again
        detailAdapter.myDataset = getActivityList().values.toTypedArray()
        detailAdapter.notifyDataSetChanged()

        return uri
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
