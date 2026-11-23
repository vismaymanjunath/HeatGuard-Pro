package com.example.appdev

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.appdev.ui.HeatGuardNavHost
import com.example.appdev.ui.theme.HeatGuardTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as HeatGuardApp).container.restoreMonitoringIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so force light system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HeatGuardTheme {
                HeatGuardNavHost()
            }
        }
    }
}
