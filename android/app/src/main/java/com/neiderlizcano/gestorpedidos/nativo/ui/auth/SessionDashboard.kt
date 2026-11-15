package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.PedidosRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.UsuariosRepository
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.PedidosRoute
import com.neiderlizcano.gestorpedidos.nativo.ui.perfil.PerfilRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class SessionNavigationViewModel : ViewModel() {
    private val selected = MutableStateFlow(false)
    val perfil = selected.asStateFlow()
    fun mostrarPerfil(value: Boolean) { selected.value = value }
}
@Composable
internal fun SessionDashboard(pedidos: PedidosRepository, usuarios: UsuariosRepository, onAccountDeleted: () -> Unit) {
    val navigation: SessionNavigationViewModel = viewModel()
    val perfil by navigation.perfil.collectAsStateWithLifecycle()
    BackHandler(enabled = perfil) { navigation.mostrarPerfil(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { navigation.mostrarPerfil(false) }, modifier = Modifier.testTag("navPedidos")) { Text(stringResource(R.string.perfil_nav_pedidos)) }
            TextButton(onClick = { navigation.mostrarPerfil(true) }, modifier = Modifier.testTag("navPerfil")) { Text(stringResource(R.string.perfil_title)) }
        }
        Box(Modifier.weight(1f)) {
            if (perfil) PerfilRoute(usuarios, onAccountDeleted) else PedidosRoute(pedidos)
        }
    }
}
