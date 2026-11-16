package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.neiderlizcano.gestorpedidos.nativo.ui.components.BotonApp
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.colorCampo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.di.ReportesRuntime
import com.neiderlizcano.gestorpedidos.nativo.di.UnavailableReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.di.AuthRuntime
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.ErrorAuth
import com.neiderlizcano.gestorpedidos.nativo.di.UsuariosRuntime
import com.neiderlizcano.gestorpedidos.nativo.di.UnavailableUsuariosRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.UsuariosRepository
import com.neiderlizcano.gestorpedidos.nativo.di.PedidosRuntime
import com.neiderlizcano.gestorpedidos.nativo.di.UnavailablePedidosRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.PedidosRepository

@Composable
fun AuthRoute(runtime: AuthRuntime) {
    val factory = remember(runtime) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AuthViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(runtime.authRepository, runtime::inicializar) as T
            }
        }
    }
    val configurationErrors = (runtime as? com.neiderlizcano.gestorpedidos.nativo.di.UnavailableAuthRuntime)?.configurationErrors.orEmpty()
    if (configurationErrors.isNotEmpty()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Configuración pendiente", style = MaterialTheme.typography.headlineMedium)
                configurationErrors.forEach { Text(it) }
                Text("Configura las propiedades Gradle de usuario fuera del repositorio y recompila la aplicación.")
            }
        }
        return
    }
    val vm: AuthViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    AuthScreen(state, vm::login, vm::recuperar, vm::solicitar, vm::verificar,
        vm::actualizar, vm::volverLogin, vm::reintentar, vm::logout, vm.privateStores,
        (runtime as? PedidosRuntime)?.pedidosRepository ?: UnavailablePedidosRepository,
        (runtime as? UsuariosRuntime)?.usuariosRepository ?: UnavailableUsuariosRepository, vm::cuentaEliminada, vm::abrirRegistro, vm::registrar, vm::corregirCampo, vm.formMemory, (runtime as? ReportesRuntime)?.reportesRepository ?: UnavailableReportesRepository)
}

@Composable
internal fun AuthScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRecover: () -> Unit,
    onRequest: (String) -> Unit,
    onVerify: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    retained: RetainedSessionStores? = null,
    pedidosRepository: PedidosRepository = UnavailablePedidosRepository,
    usuariosRepository: UsuariosRepository = UnavailableUsuariosRepository,
    onAccountDeleted: () -> Unit = onLogout,
    onOpenSignup: () -> Unit = {},
    onSignup: (String, String, String) -> Unit = { _, _, _ -> },
    onFieldEdited: (AuthValidation) -> Unit = {},
    forms: AuthFormMemory? = null,
    reportesRepository: ReportesRepository = UnavailableReportesRepository
) {
    BackHandler(enabled = state.destination == AuthDestination.FORMULARIO && state.step != AuthStep.LOGIN) {
        if (!state.busy) onBack()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.destination == AuthDestination.INICIO) {
            // Al entrar a la sesión se descarta el foco y el teclado que pudo quedar abierto desde el formulario de acceso.
            val foco = LocalFocusManager.current
            val teclado = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) { foco.clearFocus(); teclado?.hide() }
            // Solo barras del sistema y recorte (nunca el teclado): el menú y las pantallas de la sesión no deben crecer con el IME.
            val user = (state.sesion as com.neiderlizcano.gestorpedidos.nativo.domain.auth.EstadoSesion.Autenticada).usuarioId
            SessionContent(user, state.sessionVersion, retained) {
                SessionDashboard(pedidosRepository, usuariosRepository, onLogout, onAccountDeleted, logoutEnabled = !state.busy, reportes = reportesRepository)
            }
        } else {
            // Cabecera con degradado e ilustración y tarjeta blanca flotante con el formulario, como en la maqueta.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1E5B92), Color(0xFF2563EB))),
                    RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).statusBarsPadding().displayCutoutPadding()
                    .padding(top = 12.dp, bottom = 64.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_auth_hero), contentDescription = stringResource(R.string.app_name), modifier = Modifier.size(150.dp).testTag("authBrand"))
                }
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-40).dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                when (state.destination) {
                    AuthDestination.RESTAURANDO -> Text(stringResource(R.string.auth_restoring))
                    AuthDestination.ERROR_RESTAURACION -> {
                        Text(stringResource(R.string.auth_restore_error))
                        BotonApp(stringResource(R.string.auth_retry), onRetry, Modifier.fillMaxWidth().testTag("authRetry"), enabled = !state.busy)
                        TextButton(onClick = onLogout, enabled = !state.busy) { Text(stringResource(R.string.auth_return_login)) }
                    }
                    AuthDestination.FORMULARIO -> key(state.step) {
                        // No se usa rememberSaveable, Bundle ni SavedStateHandle para estas entradas.
                        val fields = forms?.obtain(state.step) ?: remember { AuthFormFields() }
                        var email by fields.email
                        var password by fields.password
                        var confirm by fields.confirmation
                        var code by fields.code
                        val title = when (state.step) {
                            AuthStep.REGISTRO -> R.string.auth_signup
                            AuthStep.REGISTRO_ENVIADO -> R.string.auth_signup
                            AuthStep.LOGIN -> R.string.auth_login_title
                            AuthStep.CORREO -> R.string.auth_recover_title
                            AuthStep.OTP -> R.string.auth_otp_title
                            AuthStep.NUEVA_PASSWORD -> R.string.auth_new_title
                            AuthStep.COMPLETADA -> R.string.auth_complete_title
                        }
                        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                        when (state.step) {
                            AuthStep.REGISTRO -> {
                                OutlinedTextField(email, { email = it; onFieldEdited(AuthValidation.EMAIL) }, label = { Text(stringResource(R.string.auth_email)) }, enabled = !state.busy, isError = localError(state, AuthValidation.EMAIL) != null,
                                    supportingText = { FieldError(localError(state, AuthValidation.EMAIL), "signupEmailError") }, colors = colorCampo(), modifier = Modifier.fillMaxWidth().testTag("signupEmail"))
                                PasswordField(password, { password = it; onFieldEdited(AuthValidation.PASSWORD); onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_password, "signupPassword", !state.busy, localError(state, AuthValidation.PASSWORD))
                                PasswordField(confirm, { confirm = it; onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_confirm, "signupConfirmation", !state.busy, localError(state, AuthValidation.CONFIRMACION))
                                BotonApp(stringResource(R.string.auth_signup), { onSignup(email, password, confirm) }, Modifier.fillMaxWidth().testTag("signupSubmit"), enabled = !state.busy, cargando = state.busy)
                            }
                            AuthStep.REGISTRO_ENVIADO -> {
                                Text(stringResource(R.string.auth_signup_sent))
                                BotonApp(stringResource(R.string.auth_login_title), onBack, Modifier.fillMaxWidth())
                            }
                            AuthStep.LOGIN, AuthStep.CORREO -> {
                                Text(stringResource(if (state.step == AuthStep.LOGIN) R.string.auth_subtitle else R.string.auth_recover_description))
                                OutlinedTextField(email, { email = it; onFieldEdited(AuthValidation.EMAIL) }, label = { Text(stringResource(R.string.auth_email)) },
                                    singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    isError = localError(state, AuthValidation.EMAIL) != null,
                                    supportingText = { FieldError(localError(state, AuthValidation.EMAIL), "authEmailError") }, colors = colorCampo(), modifier = Modifier.fillMaxWidth().testTag("authEmail"))
                                if (state.step == AuthStep.LOGIN) {
                                    PasswordField(password, { password = it; onFieldEdited(AuthValidation.PASSWORD) }, R.string.auth_password, "authPassword", !state.busy, if (AuthValidation.PASSWORD in state.invalidFields) R.string.auth_password_required else null)
                                    BotonApp(stringResource(R.string.auth_login), { onLogin(email, password) }, Modifier.fillMaxWidth().testTag("authSubmit"), enabled = !state.busy, cargando = state.busy)
                                    TextButton(onClick = onOpenSignup, enabled = !state.busy, modifier = Modifier.testTag("authSignup")) { Text(stringResource(R.string.auth_signup)) }
                                    TextButton(onClick = onRecover, enabled = !state.busy, modifier = Modifier.testTag("authRecover")) { Text(stringResource(R.string.auth_forgot)) }
                                } else BotonApp(stringResource(R.string.auth_send), { onRequest(email) }, Modifier.fillMaxWidth().testTag("authSend"), enabled = !state.busy, cargando = state.busy)
                            }
                            AuthStep.OTP -> {
                                Text(stringResource(R.string.auth_otp_description))
                                OutlinedTextField(code, { code = it; onFieldEdited(AuthValidation.CODIGO) }, label = { Text(stringResource(R.string.auth_code)) }, singleLine = true,
                                    enabled = !state.busy, isError = localError(state, AuthValidation.CODIGO) != null,
                                    supportingText = { FieldError(localError(state, AuthValidation.CODIGO), "authCodeError") }, colors = colorCampo(), modifier = Modifier.fillMaxWidth().testTag("authCode"))
                                BotonApp(stringResource(R.string.auth_verify), { onVerify(code) }, Modifier.fillMaxWidth().testTag("authVerify"), enabled = !state.busy, cargando = state.busy)
                            }
                            AuthStep.NUEVA_PASSWORD -> {
                                PasswordField(password, { password = it; onFieldEdited(AuthValidation.PASSWORD); onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_password, "authNewPassword", !state.busy, if (AuthValidation.PASSWORD in state.invalidFields) R.string.auth_password_required else null)
                                PasswordField(confirm, { confirm = it; onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_confirm, "authConfirmation", !state.busy, if (AuthValidation.CONFIRMACION in state.invalidFields) R.string.auth_confirmation_invalid else null)
                                BotonApp(stringResource(R.string.auth_update), { onUpdate(password, confirm) }, Modifier.fillMaxWidth().testTag("authUpdate"), enabled = !state.busy, cargando = state.busy)
                            }
                            AuthStep.COMPLETADA -> {
                                Text(stringResource(R.string.auth_complete_description))
                                BotonApp(stringResource(R.string.auth_login_title), onBack, Modifier.fillMaxWidth().testTag("authReturnLogin"), enabled = !state.busy)
                            }
                        }
                        // Reutiliza las entradas en memoria del formulario, sin guardarlas en el ViewModel.
                        if (state.error == ErrorAuth.SERVICIO_NO_DISPONIBLE && state.retryStep == state.step &&
                            state.step in setOf(AuthStep.LOGIN, AuthStep.CORREO, AuthStep.OTP)) {
                            TextButton(onClick = {
                                when (state.step) {
                            AuthStep.LOGIN -> onLogin(email, password)
                                    AuthStep.CORREO -> onRequest(email)
                                    AuthStep.OTP -> onVerify(code)
                                    else -> Unit
                                }
                            }, enabled = !state.busy, modifier = Modifier.testTag("authRetry")) { Text(stringResource(R.string.auth_retry)) }
                        }
                        if (state.step != AuthStep.LOGIN && state.step != AuthStep.COMPLETADA) TextButton(onClick = onBack, enabled = !state.busy) { Text(stringResource(R.string.auth_back)) }
                    }
                    AuthDestination.INICIO -> Unit
                }
                if (state.busy || state.destination == AuthDestination.RESTAURANDO) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("authProgress"))
                    Text(stringResource(R.string.auth_loading))
                }
                state.error?.let { Text(stringResource(errorMessage(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("authError")) }
                if (state.logoutNoConfirmado) Text(stringResource(R.string.auth_logout_unconfirmed))
                }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: Int, tag: String, enabled: Boolean, error: Int? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, singleLine = true, enabled = enabled, colors = colorCampo(),
        isError = error != null, supportingText = { error?.let { Text(stringResource(it), modifier = Modifier.testTag(tag + "Error")) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = enabled, modifier = Modifier.testTag(tag + "Visibility")) {
            Text(stringResource(if (visible) R.string.auth_hide else R.string.auth_show))
        } }, modifier = Modifier.fillMaxWidth().testTag(tag))
}

private fun errorMessage(error: ErrorAuth): Int = when (error) {
    ErrorAuth.CREDENCIALES_RECHAZADAS -> R.string.auth_bad_credentials
    ErrorAuth.SESION_RECHAZADA -> R.string.auth_session_rejected
    ErrorAuth.OTP_INVALIDO_O_EXPIRADO -> R.string.auth_otp_expired
    ErrorAuth.ENTRADA_RECHAZADA -> R.string.auth_entry_rejected
    ErrorAuth.LIMITE_SOLICITUDES -> R.string.auth_rate_limit
    ErrorAuth.CONEXION -> R.string.auth_connection
    ErrorAuth.SERVICIO_NO_DISPONIBLE -> R.string.auth_service
    ErrorAuth.RESPUESTA_INVALIDA -> R.string.auth_invalid_response
    ErrorAuth.ALMACENAMIENTO -> R.string.auth_storage
    ErrorAuth.NO_CLASIFICADO -> R.string.auth_unknown
}

private fun localError(state: AuthUiState, field: AuthValidation): Int? {
    if (field !in state.invalidFields && state.validation != field) return null
    return when (field) {
        AuthValidation.EMAIL -> R.string.auth_email_invalid
        AuthValidation.PASSWORD -> R.string.auth_password_required
        AuthValidation.CODIGO -> R.string.auth_code_required
        AuthValidation.CONFIRMACION -> R.string.auth_confirmation_invalid
    }
}
@Composable
private fun FieldError(error: Int?, tag: String) {
    error?.let { Text(stringResource(it), modifier = Modifier.testTag(tag)) }
}
