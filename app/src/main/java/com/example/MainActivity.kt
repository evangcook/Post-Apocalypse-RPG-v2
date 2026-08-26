package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.TextView
import android.graphics.Color
import android.widget.ScrollView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // We are bypassing Jetpack Compose entirely.
            val debugView = TextView(this)
            debugView.text = "SYSTEM ONLINE. \n\nJETPACK COMPOSE IS THE FRACTURE POINT."
            debugView.setTextColor(Color.GREEN)
            debugView.setBackgroundColor(Color.BLACK)
            debugView.textSize = 20f
            debugView.setPadding(50, 50, 50, 50)
            setContentView(debugView)
            
        } catch (t: Throwable) {
            // If the native view fails, it will print its own death certificate.
            val errorView = TextView(this)
            errorView.text = "FATAL CRASH:\n\n" + t.stackTraceToString()
            errorView.setTextColor(Color.RED)
            errorView.setBackgroundColor(Color.BLACK)
            errorView.textSize = 12f
            errorView.setPadding(40, 40, 40, 40)
            
            val scroller = ScrollView(this)
            scroller.setBackgroundColor(Color.BLACK)
            scroller.addView(errorView)
            setContentView(scroller)
        }
    }
}
