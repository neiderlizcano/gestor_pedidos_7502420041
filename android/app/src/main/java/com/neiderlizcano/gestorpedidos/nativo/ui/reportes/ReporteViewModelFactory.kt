package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Fábrica mínima: cada ViewModel recibe el repositorio por constructor, sin contenedor global. */
internal fun <VM : ViewModel> reporteFactory(clase: Class<VM>, crear: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == clase)
            @Suppress("UNCHECKED_CAST")
            return crear() as T
        }
    }
