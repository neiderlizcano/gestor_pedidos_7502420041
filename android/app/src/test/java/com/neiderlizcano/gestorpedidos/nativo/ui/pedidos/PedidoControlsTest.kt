package com.neiderlizcano.gestorpedidos.nativo.ui.pedidos
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class PedidoControlsTest {
 @Test fun datesRoundTripUtcWithoutBogotaDayShift() {
  for (value in listOf("2024-02-29","2026-11-15","1999-12-31","2101-01-01")) {
   val date=LocalDate.parse(value);assertEquals(date,pickerDate(date.pickerMillis()))
  }
 }
 @Test fun statusSelectorHasExactlyTheSixRequiredOptionsInOrder() {
  assertEquals(listOf("Seleccione un estado","Pendiente","En preparación","Enviado","Entregado","Cancelado"),OPCIONES_ESTADO)
  assertEquals(listOf("Pendiente","En preparación","Enviado","Entregado","Cancelado"),ESTADOS_PEDIDO)
 }
 @Test fun placeholderEmptyOrUnknownStatusCannotBeSavedAndEachListedOneCan() {
  fun issue(value:String)=PedidoDraft.from(samplePedido()).with(PedidoField.ESTADO,value).issues()[PedidoField.ESTADO]
  for(invalid in listOf("","  ","Seleccione un estado","En reparto","pendiente","Preparación especial")) assertEquals(invalid,FieldIssue.ESTADO_INVALIDO,issue(invalid))
  for(valid in ESTADOS_PEDIDO) assertNull(valid,issue(valid))
 }
 @Test fun epochDateDoesNotDependOnDeviceTimezone() {
  val before=java.util.TimeZone.getDefault()
  try {
   java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Bogota"))
   assertEquals(0L,LocalDate.of(1970,1,1).pickerMillis())
   assertEquals(LocalDate.of(1970,1,1),pickerDate(0L))
  } finally { java.util.TimeZone.setDefault(before) }
 }

}
