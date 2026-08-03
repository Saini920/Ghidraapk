package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.navigation.AppNavigation
import com.example.ui.theme.AppTheme
import com.example.viewmodel.AppViewModel
import androidx.compose.foundation.layout.Box

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Box(modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize()) {
                    AppNavigation(viewModel)
                    com.example.ui.components.DebuggerOverlay()
                }
            }
        }
    }
}
