package com.neiderlizcano.gestorpedidos.nativo.ui.auth
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.time.LocalDate
internal fun selectPedidoDate(rule: ComposeContentTestRule, context: Context, value: String) {
    val date = LocalDate.parse(value)
    rule.onNodeWithTag("pedidoDateMode").performClick()
    val input = android.text.format.DateFormat.getDateFormatOrder(context).joinToString("") {
        when (it) { 'd' -> date.dayOfMonth.toString().padStart(2,'0'); 'M' -> date.monthValue.toString().padStart(2,'0'); else -> date.year.toString().padStart(4,'0') }
    }
    rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("pedidoDatePicker"))).performTextReplacement(input)
    rule.onNodeWithTag("pedidoDateConfirm").performClick()
}
