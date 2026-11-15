package com.neiderlizcano.gestorpedidos.nativo.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test fun introductionIsHiddenOnLaunch() {
        assertFalse(HomeViewModel().uiState.value.showIntroduction)
    }

    @Test fun introductionCanBeDismissedAndOpenedAgain() {
        val viewModel = HomeViewModel()
        viewModel.showIntroduction()
        assertTrue(viewModel.uiState.value.showIntroduction)
        viewModel.dismissIntroduction()
        assertFalse(viewModel.uiState.value.showIntroduction)
        viewModel.showIntroduction()
        assertTrue(viewModel.uiState.value.showIntroduction)
    }

    @Test fun dismissingAnAlreadyHiddenIntroductionKeepsItHidden() {
        val viewModel = HomeViewModel()
        viewModel.dismissIntroduction()
        assertFalse(viewModel.uiState.value.showIntroduction)
    }
}
