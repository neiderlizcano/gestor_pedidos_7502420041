package com.neiderlizcano.gestorpedidos.nativo.di
import org.junit.Assert.*
import org.junit.Test
class PublicConfigurationTest {
 private val auth = "https://proyecto.invalid/auth/v1/"
 private val api = "https://backend.invalid/"
 private val key = "sb_publishable_solo_pruebas"
 @Test fun missingValuesBlockInitializationWithoutExposingValues() {
  var calls = 0
  val result = PublicConfiguration.initialize("", "", "") { _,_,_ -> calls++; UnavailableAuthRuntime() }
  assertEquals(0,calls); assertEquals(3,(result as UnavailableAuthRuntime).configurationErrors.size)
 }
 @Test fun validConfigurationReachesFactoryExactlyOnce() {
  var calls = 0; val expected = UnavailableAuthRuntime()
  val actual = PublicConfiguration.initialize(auth,key,api) { a,k,b ->
   calls++; assertEquals(auth,a); assertEquals(key,k); assertEquals(api,b); expected
  }
  assertSame(expected,actual); assertEquals(1,calls)
 }
 @Test fun rejectsHttpDuplicatePathsAndMissingTrailingSlash() {
  for (bad in listOf("http://proyecto.invalid/auth/v1/", "https://proyecto.invalid/", auth+"auth/v1/", auth.dropLast(1), auth+"?x=1", auth.replace("proyecto", "proy\necto")))
   assertTrue(PublicConfiguration.errors(bad,key,api).isNotEmpty())
  for (bad in listOf("http://backend.invalid/", api+"api/v1/",api.dropLast(1),"https://usuario:privado@backend.invalid/"))
   assertTrue(PublicConfiguration.errors(auth,key,bad).isNotEmpty())
 }
 @Test fun rejectsPrivilegedKeyWithoutEchoingIt() {
  val privateKey = "sb_secret_valor_ficticio"
  val errors = PublicConfiguration.errors(auth,privateKey,api)
  assertTrue(errors.isNotEmpty()); assertFalse(errors.joinToString().contains(privateKey))
 }
 @Test fun initializationFailureIsSanitized() {
  val result = PublicConfiguration.initialize(auth,key,api) { _,_,_ -> throw IllegalArgumentException("valor_confidencial_ficticio") }
  assertFalse((result as UnavailableAuthRuntime).configurationErrors.joinToString().contains("valor_confidencial_ficticio"))
 }
}
