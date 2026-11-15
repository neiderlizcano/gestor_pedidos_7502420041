package com.neiderlizcano.gestorpedidos.nativo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neiderlizcano.gestorpedidos.nativo.ui.home.HomeRoute
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GestorPedidosTheme { HomeRoute() } }
    }
}
