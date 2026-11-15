package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionContentTest {
    @get:Rule val compose = createComposeRule()
    class PrivateViewModel : ViewModel() {
        var data = ""
        var cleared = false
        override fun onCleared() { cleared = true }
    }
    @Test fun accountSwitchLogoutAndSameUserReloginDisposePrivateModels() {
        val user = mutableStateOf<String?>("A")
        var current: PrivateViewModel? = null
        compose.setContent {
            user.value?.let { id -> SessionContent(id) { current = viewModel<PrivateViewModel>() } }
        }
        compose.waitForIdle()
        val first = current!!
        compose.runOnIdle { first.data = "private-A"; user.value = "B" }
        compose.waitForIdle()
        val second = current!!
        assertTrue(first.cleared); assertNotSame(first, second); assertEquals("", second.data)
        compose.runOnIdle { second.data = "private-B"; user.value = null }
        compose.waitForIdle(); assertTrue(second.cleared)
        compose.runOnIdle { user.value = "B" }
        compose.waitForIdle()
        assertNotSame(second, current); assertEquals("", current!!.data)
    }
    @Test fun logoutAThenLoginBStartsWithEmptyStore() {
        val user = mutableStateOf<String?>("A")
        var current: PrivateViewModel? = null
        compose.setContent { user.value?.let { id -> SessionContent(id) { current = viewModel<PrivateViewModel>() } } }
        compose.waitForIdle(); val first = current!!
        compose.runOnIdle { first.data = "private-A"; user.value = null }
        compose.waitForIdle(); assertTrue(first.cleared)
        compose.runOnIdle { user.value = "B" }
        compose.waitForIdle(); assertNotSame(first, current); assertEquals("", current!!.data)
    }
    @Test fun sameUserNewGenerationCannotReusePrivateStore() {
        val version = mutableStateOf(0L)
        var current: PrivateViewModel? = null
        compose.setContent { SessionContent("A", version.value) { current = viewModel<PrivateViewModel>() } }
        compose.waitForIdle(); val first = current!!
        compose.runOnIdle { first.data = "private-A"; version.value++ }
        compose.waitForIdle(); assertTrue(first.cleared); assertNotSame(first, current); assertEquals("", current!!.data)
    }

}
