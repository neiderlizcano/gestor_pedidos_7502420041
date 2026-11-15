package com.neiderlizcano.gestorpedidos.nativo.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HomeUiState(val showIntroduction: Boolean = false)

class HomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val uiState = mutableState.asStateFlow()

    fun showIntroduction() { mutableState.update { it.copy(showIntroduction = true) } }
    fun dismissIntroduction() { mutableState.update { it.copy(showIntroduction = false) } }
}
