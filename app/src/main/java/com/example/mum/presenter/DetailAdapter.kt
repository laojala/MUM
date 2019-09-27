package com.example.mum.presenter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.mum.R
import com.example.mum.model.DetailItem

class DetailAdapter(public var myDataset: Array<DetailItem>) : RecyclerView.Adapter<DetailAdapter.MyViewHolder>() {

    class MyViewHolder(val view: View, val description: TextView, val value: TextView, val score: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): MyViewHolder {
        val layoutInflater = LayoutInflater.from(p0.context)
        val container = layoutInflater.inflate(R.layout.detailed_activity_item, p0, false)

        val description = container.findViewById<TextView>(R.id.activity_description)
        val value = container.findViewById<TextView>(R.id.activity_value)
        val score = container.findViewById<TextView>(R.id.activity_score)

        return MyViewHolder(container, description, value, score)
    }

    override fun getItemCount(): Int {
        return myDataset.size
    }

    override fun onBindViewHolder(p0: MyViewHolder, p1: Int) {
        p0.description.text = myDataset[p1].description
        p0.value.text = myDataset[p1].value.toString()
        p0.score.text = myDataset[p1].score.toString()
        if (myDataset[p1].score >= 0) {
            p0.score.setBackgroundResource(R.color.positiveColor)
        }
        else {
            p0.score.setBackgroundResource(R.color.negativeColor)
        }
    }
}