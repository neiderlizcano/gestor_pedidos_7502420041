package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.PedidosRepository
import com.neiderlizcano.gestorpedidos.nativo.ui.components.BotonApp
import com.neiderlizcano.gestorpedidos.nativo.ui.components.EstiloBoton
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.UsuariosRepository
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.PedidosRoute
import com.neiderlizcano.gestorpedidos.nativo.ui.perfil.PerfilRoute
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.di.UnavailableReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.ui.reportes.ReportesRoute
import com.neiderlizcano.gestorpedidos.nativo.sensores.SensoresRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Destinos de la sesión autenticada. */
internal enum class Destino { MENU, PEDIDOS, PERFIL, REPORTES, SENSORES }

/** Navegación de la sesión: destino actual y petición pendiente de abrir «Nuevo pedido». */
internal class SessionNavigationViewModel : ViewModel() {
    private val destino = MutableStateFlow(Destino.MENU)
    private val nuevo = MutableStateFlow(false)
    val actual = destino.asStateFlow()
    /** Verdadero hasta que la pantalla de pedidos atiende la petición de «Nuevo pedido» (se abre un solo formulario). */
    val nuevoPendiente = nuevo.asStateFlow()
    fun ir(value: Destino) { destino.value = value }
    fun abrirNuevoPedido() { nuevo.value = true; destino.value = Destino.PEDIDOS }
    fun nuevoAtendido() { nuevo.value = false }
}

private val AzulOscuro = Color(0xFF1E5B92)
private val AzulPrincipal = Color(0xFF2563EB)

/**
 * Menú central de la sesión: CRUD, reportes propios y sensores comparten el propietario de ViewModels autenticado.
 */
@Composable
internal fun SessionDashboard(
    pedidos: PedidosRepository, usuarios: UsuariosRepository, onLogout: () -> Unit, onAccountDeleted: () -> Unit,
    logoutEnabled: Boolean = true, reportes: ReportesRepository = UnavailableReportesRepository
) {
    val navigation: SessionNavigationViewModel = viewModel()
    val destino by navigation.actual.collectAsStateWithLifecycle()
    val nuevoPendiente by navigation.nuevoPendiente.collectAsStateWithLifecycle()
    BackHandler(enabled = destino != Destino.MENU) { navigation.ir(Destino.MENU) }
    when (destino) {
        Destino.MENU -> MenuPrincipal(
            onPedidos = { navigation.ir(Destino.PEDIDOS) }, onNuevo = navigation::abrirNuevoPedido,
            onPerfil = { navigation.ir(Destino.PERFIL) }, onReportes = { navigation.ir(Destino.REPORTES) },
            onSensores = { navigation.ir(Destino.SENSORES) }, onLogout = onLogout, logoutEnabled = logoutEnabled)
        else -> Column(Modifier.fillMaxSize()) {
            BarraSuperior(titulo = stringResource(when (destino) {
                Destino.PERFIL -> R.string.menu_perfil
                Destino.REPORTES -> R.string.menu_reportes
                Destino.SENSORES -> R.string.menu_sensores
                else -> R.string.menu_pedidos
            }),
                onVolver = { navigation.ir(Destino.MENU) })
            Box(Modifier.weight(1f)) {
                when (destino) {
                    Destino.PERFIL -> PerfilRoute(usuarios, onAccountDeleted)
                    Destino.REPORTES -> ReportesRoute(reportes)
                    Destino.SENSORES -> SensoresRoute()
                    else -> PedidosRoute(pedidos, nuevoPendiente, navigation::nuevoAtendido)
                }
            }
        }
    }
}

/** Barra azul oscuro de las pantallas internas: un único botón para volver al menú. */
@Composable
private fun BarraSuperior(titulo: String, onVolver: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(AzulOscuro).statusBarsPadding().displayCutoutPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onVolver, modifier = Modifier.testTag("navInicio")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.menu_back_home), tint = Color.White)
        }
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
private fun MenuPrincipal(onPedidos: () -> Unit, onNuevo: () -> Unit, onPerfil: () -> Unit, onReportes: () -> Unit, onSensores: () -> Unit, onLogout: () -> Unit, logoutEnabled: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("menuPrincipal")) {
        // Cabecera con degradado azul, como en la maqueta.
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AzulOscuro, AzulPrincipal)),
            RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding().displayCutoutPadding().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(stringResource(R.string.menu_subtitle), style = MaterialTheme.typography.labelLarge, color = Color(0xFFDCEAFE))
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                TarjetaMenu(R.string.menu_pedidos, R.string.menu_pedidos_desc, Icons.AutoMirrored.Filled.List, "navPedidos", Modifier.weight(1f).fillMaxHeight(), orden = 0, onClick = onPedidos)
                TarjetaMenu(R.string.menu_nuevo, R.string.menu_nuevo_desc, Icons.Filled.Add, "navNuevo", Modifier.weight(1f).fillMaxHeight(), orden = 1, onClick = onNuevo)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                TarjetaMenu(R.string.menu_reportes, R.string.menu_reportes_desc, Icons.Filled.Info, "navReportes", Modifier.weight(1f).fillMaxHeight(), orden = 2, onClick = onReportes)
                TarjetaMenu(R.string.menu_sensores, R.string.menu_sensores_desc, Icons.Filled.Settings, "navSensores", Modifier.weight(1f).fillMaxHeight(), orden = 3, onClick = onSensores)
            }
            TarjetaMenu(R.string.menu_perfil, R.string.menu_perfil_desc, Icons.Filled.Person, "navPerfil", Modifier.fillMaxWidth(), orden = 4, onClick = onPerfil)
            BotonApp(stringResource(R.string.auth_logout), onLogout, Modifier.fillMaxWidth().testTag("authLogout"), EstiloBoton.SECUNDARIO,
                Icons.AutoMirrored.Filled.ExitToApp, enabled = logoutEnabled)
        }
    }
}

@Composable
private fun TarjetaMenu(
    title: Int, subtitle: Int, icon: ImageVector, tag: String, modifier: Modifier, orden: Int, enabled: Boolean = true, onClick: () -> Unit = {}
) {
    // Entrada escalonada (aparece y sube) y reducción elástica al pulsar; sin animaciones infinitas.
    var entro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entro = true }
    val aparicion by animateFloatAsState(if (entro) 1f else 0f, tween(durationMillis = 350, delayMillis = orden * 70), label = "aparicionTarjeta")
    val interaccion = remember { MutableInteractionSource() }
    val presionada by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(if (presionada && enabled) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "escalaTarjeta")
    ElevatedCard(onClick = onClick, enabled = enabled, interactionSource = interaccion, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.heightIn(min = 112.dp).graphicsLayer {
            alpha = aparicion; translationY = (1f - aparicion) * 24.dp.toPx(); scaleX = escala; scaleY = escala
        }.testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(40.dp).background(if (enabled) Color(0xFFDBEAFE) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (enabled) AzulPrincipal else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
        }
    }
}
