package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
        (runtime as? UsuariosRuntime)?.usuariosRepository ?: UnavailableUsuariosRepository, vm::cuentaEliminada, vm::abrirRegistro, vm::registrar, vm::corregirCampo, vm.formMemory)
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
    forms: AuthFormMemory? = null
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
            Column(Modifier.fillMaxSize()) {
                // Solo barras del sistema y recorte: safeDrawingPadding() incluía el teclado (IME) y el encabezado crecía
                // con su altura, dejando la pantalla en blanco al escribir o al abrir sesión con el teclado visible.
                Row(Modifier.fillMaxWidth().statusBarsPadding().displayCutoutPadding().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.auth_active), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onLogout, enabled = !state.busy, modifier = Modifier.testTag("authLogout")) { Text(stringResource(R.string.auth_logout)) }
                }
                Box(Modifier.weight(1f)) {
                    val user = (state.sesion as com.neiderlizcano.gestorpedidos.nativo.domain.auth.EstadoSesion.Autenticada).usuarioId
                    SessionContent(user, state.sessionVersion, retained) { SessionDashboard(pedidosRepository, usuariosRepository, onAccountDeleted) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).imePadding().padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Icon(painterResource(R.drawable.ic_auth_package), contentDescription = stringResource(R.string.app_name), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp).testTag("authBrand"))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                when (state.destination) {
                    AuthDestination.RESTAURANDO -> Text(stringResource(R.string.auth_restoring))
                    AuthDestination.ERROR_RESTAURACION -> {
                        Text(stringResource(R.string.auth_restore_error))
                        Button(onClick = onRetry, enabled = !state.busy, modifier = Modifier.testTag("authRetry")) { Text(stringResource(R.string.auth_retry)) }
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
                                    supportingText = { FieldError(localError(state, AuthValidation.EMAIL), "signupEmailError") }, modifier = Modifier.testTag("signupEmail"))
                                PasswordField(password, { password = it; onFieldEdited(AuthValidation.PASSWORD); onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_password, "signupPassword", !state.busy, localError(state, AuthValidation.PASSWORD))
                                PasswordField(confirm, { confirm = it; onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_confirm, "signupConfirmation", !state.busy, localError(state, AuthValidation.CONFIRMACION))
                                Button(onClick = { onSignup(email, password, confirm) }, enabled = !state.busy, modifier = Modifier.testTag("signupSubmit")) { Text(stringResource(R.string.auth_signup)) }
                            }
                            AuthStep.REGISTRO_ENVIADO -> {
                                Text(stringResource(R.string.auth_signup_sent))
                                Button(onClick = onBack) { Text(stringResource(R.string.auth_login_title)) }
                            }
                            AuthStep.LOGIN, AuthStep.CORREO -> {
                                Text(stringResource(if (state.step == AuthStep.LOGIN) R.string.auth_subtitle else R.string.auth_recover_description))
                                OutlinedTextField(email, { email = it; onFieldEdited(AuthValidation.EMAIL) }, label = { Text(stringResource(R.string.auth_email)) },
                                    singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    isError = localError(state, AuthValidation.EMAIL) != null,
                                    supportingText = { FieldError(localError(state, AuthValidation.EMAIL), "authEmailError") }, modifier = Modifier.fillMaxWidth().testTag("authEmail"))
                                if (state.step == AuthStep.LOGIN) {
                                    PasswordField(password, { password = it; onFieldEdited(AuthValidation.PASSWORD) }, R.string.auth_password, "authPassword", !state.busy, if (AuthValidation.PASSWORD in state.invalidFields) R.string.auth_password_required else null)
                                    Button(onClick = { onLogin(email, password) }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("authSubmit")) { Text(stringResource(R.string.auth_login)) }
                                    TextButton(onClick = onOpenSignup, enabled = !state.busy, modifier = Modifier.testTag("authSignup")) { Text(stringResource(R.string.auth_signup)) }
                                    TextButton(onClick = onRecover, enabled = !state.busy, modifier = Modifier.testTag("authRecover")) { Text(stringResource(R.string.auth_forgot)) }
                                } else Button(onClick = { onRequest(email) }, enabled = !state.busy, modifier = Modifier.testTag("authSend")) { Text(stringResource(R.string.auth_send)) }
                            }
                            AuthStep.OTP -> {
                                Text(stringResource(R.string.auth_otp_description))
                                OutlinedTextField(code, { code = it; onFieldEdited(AuthValidation.CODIGO) }, label = { Text(stringResource(R.string.auth_code)) }, singleLine = true,
                                    enabled = !state.busy, isError = localError(state, AuthValidation.CODIGO) != null,
                                    supportingText = { FieldError(localError(state, AuthValidation.CODIGO), "authCodeError") }, modifier = Modifier.fillMaxWidth().testTag("authCode"))
                                Button(onClick = { onVerify(code) }, enabled = !state.busy, modifier = Modifier.testTag("authVerify")) { Text(stringResource(R.string.auth_verify)) }
                            }
                            AuthStep.NUEVA_PASSWORD -> {
                                PasswordField(password, { password = it; onFieldEdited(AuthValidation.PASSWORD); onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_password, "authNewPassword", !state.busy, if (AuthValidation.PASSWORD in state.invalidFields) R.string.auth_password_required else null)
                                PasswordField(confirm, { confirm = it; onFieldEdited(AuthValidation.CONFIRMACION) }, R.string.auth_confirm, "authConfirmation", !state.busy, if (AuthValidation.CONFIRMACION in state.invalidFields) R.string.auth_confirmation_invalid else null)
                                Button(onClick = { onUpdate(password, confirm) }, enabled = !state.busy, modifier = Modifier.testTag("authUpdate")) { Text(stringResource(R.string.auth_update)) }
                            }
                            AuthStep.COMPLETADA -> {
                                Text(stringResource(R.string.auth_complete_description))
                                Button(onClick = onBack, enabled = !state.busy, modifier = Modifier.testTag("authReturnLogin")) { Text(stringResource(R.string.auth_login_title)) }
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

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: Int, tag: String, enabled: Boolean, error: Int? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, singleLine = true, enabled = enabled,
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
