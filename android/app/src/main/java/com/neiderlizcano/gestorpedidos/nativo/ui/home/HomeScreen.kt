package com.neiderlizcano.gestorpedidos.nativo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::showIntroduction, viewModel::dismissIntroduction)
}

@Composable
fun HomeScreen(state: HomeUiState, onIntroduction: () -> Unit, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(stringResource(R.string.app_monogram), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large).padding(20.dp))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_description), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onIntroduction) { Text(stringResource(R.string.home_action)) }
            Text(stringResource(R.string.home_note), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (state.showIntroduction) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(stringResource(R.string.home_details)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) } })
    }
}
