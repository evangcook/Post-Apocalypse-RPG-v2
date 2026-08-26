package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val debugView = TextView(this)
        debugView.text = "BARE METAL SUCCESS.\n\nCOMPONENT ACTIVITY IS THE FRACTURE."
        debugView.setTextColor(Color.GREEN)
        debugView.setBackgroundColor(Color.BLACK)
        debugView.textSize = 20f
        debugView.gravity = Gravity.CENTER
        
        setContentView(debugView)
    }
}

