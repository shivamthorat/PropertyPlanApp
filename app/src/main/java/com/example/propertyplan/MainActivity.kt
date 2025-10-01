package com.example.propertyplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.propertyplan.ui.AppTheme
import com.example.propertyplan.ui.screens.SmartPlanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                SmartPlanScreen()
            }
        }
    }
}
