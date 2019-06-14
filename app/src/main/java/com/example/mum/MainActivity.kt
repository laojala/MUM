package com.example.mum

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field.FIELD_STEPS
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var detailAdapter: DetailAdapter
    private lateinit var mChart: LineChart

    companion object {
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 10 // for later reference
        const val USE_DUMMY_VALUES = true
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

//        addDummyHistoryData()

        // set up the list with the details for the current day
        val dummyList = getActivityList()

        val viewManager = LinearLayoutManager(this)
        detailAdapter = DetailAdapter(dummyList.values.toTypedArray())

        findViewById<RecyclerView>(R.id.detail_item_list).apply {
            layoutManager = viewManager
            adapter = detailAdapter
        }

        mChart = findViewById(R.id.temp_plot)
    }


    override fun onResume() {
        super.onResume()

        // Create the connection to the Fitness API
        handleGoogleSignIn()

        displayScore()

    }

    private fun displayScore() {


        val currentScore = getActivityList().values.sumBy { it.score }

        detailAdapter.myDataset = getActivityList().values.toTypedArray()
        detailAdapter.notifyDataSetChanged()

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

        createHistoryGraph()

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

        return getActivityListForDays(1, 0)

    }

    private fun getActivityListForDays(startDay : Int, endDay : Int) : HashMap<String, DetailItem>  {
        val activityItems = hashMapOf<String, DetailItem>()

        val lastDay = Calendar.getInstance()
        lastDay.set(Calendar.HOUR_OF_DAY, 0)
        lastDay.set(Calendar.MINUTE, 0)
        lastDay.set(Calendar.SECOND, 0)
        lastDay.set(Calendar.MILLISECOND, 0)
        lastDay.add(Calendar.DAY_OF_YEAR, -endDay + 1)

        val firstDay = Calendar.getInstance()
        firstDay.set(Calendar.HOUR_OF_DAY, 0)
        firstDay.set(Calendar.MINUTE, 0)
        firstDay.set(Calendar.SECOND, 0)
        firstDay.set(Calendar.MILLISECOND, 0)
        firstDay.add(Calendar.DAY_OF_YEAR, -startDay)

        val data = contentResolver.query(Provider.Activity_Data.CONTENT_URI, null, Provider.Activity_Data.TIMESTAMP + " > " + firstDay.timeInMillis + " AND " + Provider.Activity_Data.TIMESTAMP + " < " + lastDay.timeInMillis, null, Provider.Activity_Data.TIMESTAMP + " DESC")

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

        displayScore()

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

    private fun createHistoryGraph() {

        val date = Calendar.getInstance()
        date.add(Calendar.DAY_OF_YEAR, -4)

        val scores = getScoresForLastDays(5).map{ it.value }
        println("scores of the last days $scores")

        // create the list of the data
        val entries = arrayListOf<Entry>()
        for (i in 0..4) {
            // TODO
            if (USE_DUMMY_VALUES) {
                entries.add(Entry(date.timeInMillis.toFloat(), (Math.random() * 400).toFloat() - 200.toFloat()))
            }
            else {
                entries.add(Entry(date.timeInMillis.toFloat(), scores[i].toFloat()))
            }
            date.add(Calendar.DAY_OF_YEAR, 1)
        }

        val dataSet = LineDataSet(entries, resources.getString(R.string.graph_description))
        dataSet.color = ContextCompat.getColor(this, R.color.colorPrimary)
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(true)
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
        dataSet.lineWidth = 2f

        val data = LineData(dataSet)

        mChart.description.setEnabled(false)
        mChart.data = data
        mChart.invalidate() //refresh

        val params = mChart.layoutParams
        params.height = 380
        mChart.layoutParams = params
        mChart.contentDescription = ""
        mChart.setBackgroundColor(Color.WHITE)
        mChart.setDrawGridBackground(false)
        mChart.setDrawBorders(false)

        val left = mChart.axisLeft
        left.setDrawLabels(true)
        left.setDrawGridLines(false)
        left.setDrawAxisLine(false)
        left.granularity = 1.toFloat()
        left.isGranularityEnabled = true
        left.setDrawZeroLine(true)

        val right = mChart.axisRight
        right.setDrawAxisLine(false)
        right.setDrawLabels(false)
        right.setDrawGridLines(false)

        val bottom = mChart.xAxis
        bottom.position = XAxis.XAxisPosition.BOTTOM
        bottom.setDrawGridLines(false)
        bottom.isGranularityEnabled = true
        bottom.granularity = 1.toFloat()

        val l = mChart.legend
        l.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        l.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        l.form = Legend.LegendForm.LINE
        l.typeface = Typeface.DEFAULT_BOLD

        val xAxis = mChart.xAxis
        xAxis.setCenterAxisLabels(true);
        xAxis.valueFormatter = object : ValueFormatter() {

            val mFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)

            override fun getFormattedValue(value : Float): String {
                return mFormat.format(Date(value.toLong()))
            }
        }

    }

    private fun getScoresForLastDays(numberOfDays: Int) : HashMap<Calendar, Int> {

        val scoreList = hashMapOf<Calendar, Int>()

        for (i in numberOfDays downTo 1) {
            val currentDay = Calendar.getInstance()
            currentDay.set(Calendar.HOUR_OF_DAY, 0)
            currentDay.set(Calendar.MINUTE, 0)
            currentDay.set(Calendar.SECOND, 0)
            currentDay.set(Calendar.MILLISECOND, 0)
            currentDay.add(Calendar.DAY_OF_YEAR, -i)

            val activityList = getActivityListForDays(i, i - 1)
            scoreList[currentDay] = activityList.values.sumBy { it.score }
        }

        return scoreList
    }

    private fun addDummyHistoryData() {

        val scores = intArrayOf(242, 123, -12, 34)
        val today = Calendar.getInstance()

        for (i in 0 until scores.size) {
            today.add(Calendar.DAY_OF_YEAR, -i - 1)

            val values = ContentValues()

            values.put(Provider.Activity_Data.TIMESTAMP, today.timeInMillis)
            values.put(Provider.Activity_Data.DEVICE_ID, Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
            values.put(Provider.Activity_Data.SENSOR_TYPE, "step_count")
            values.put(Provider.Activity_Data.VALUE, scores[i])
            values.put(
                Provider.Activity_Data.SCORE,
                scores[i]
            )

            applicationContext.contentResolver.insert(Provider.Activity_Data.CONTENT_URI, values)
        }

    }
}
