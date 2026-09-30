package com.turnero.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.turnero.app.ui.navigation.TurneroNavGraph
import com.turnero.app.ui.theme.TurneroTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TurneroTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // El `Text("Turnero")` hardcodeado era la excepcion temporal del
                    // slice 0. A partir de aca la activity solo monta el grafo.
                    TurneroNavGraph()
                }
            }
        }
    }
}
