package com.neiderlizcano.gestorpedidos.nativo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neiderlizcano.gestorpedidos.nativo.ui.auth.AuthRoute
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = (application as GestorPedidosApplication).auth
        setContent { GestorPedidosTheme { AuthRoute(auth) } }
    }
}
