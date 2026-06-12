package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.P2PViewModel
import com.example.ui.viewmodel.P2PViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: P2PViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize viewmodel with our custom factory integrating Room
        viewModel = ViewModelProvider(
            this, 
            P2PViewModelFactory(applicationContext)
        )[P2PViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}
