package com.lichong.calibrereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lichong.calibrereader.ui.AppRoot
import com.lichong.calibrereader.ui.theme.CalibreTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CalibreApp).container
        setContent {
            CalibreTheme {
                AppRoot(container)
            }
        }
    }
}
