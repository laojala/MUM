package com.example.mum

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button

class InstructionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)

        val button = findViewById<Button>(R.id.instructions)
        button.setOnClickListener {
            // Apply hide flag after instructions are shown
            val pref = (applicationContext).getSharedPreferences("instructions", Context.MODE_PRIVATE)
            pref.edit().putBoolean("hidden", true).apply()

            val main = Intent(this, MainActivity::class.java)
            startActivity(main)
            finish()
        }
    }



}
