package com.neiderlizcano.gestorpedidos.nativo.core.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

// No response values or causes are retained in this public exception.
class InvalidPayloadException : IOException("Respuesta API inválida.")

fun <T : Any> requiredField(value: T?): T = value ?: throw InvalidPayloadException()

object NetworkJson {
    fun create(): Gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .registerTypeAdapter(LocalDate::class.java, DateAdapter().nullSafe())
        .registerTypeAdapter(BigDecimal::class.java, DecimalAdapter().nullSafe())
        .registerTypeAdapter(String::class.java, StrictStringAdapter().nullSafe())
        .registerTypeAdapter(Long::class.javaObjectType, StrictLongAdapter().nullSafe())
        .registerTypeAdapter(Int::class.javaObjectType, StrictIntAdapter().nullSafe())
        .registerTypeAdapter(Boolean::class.javaObjectType, StrictBooleanAdapter().nullSafe())
        .create()

    // Gson normally coerces numeric/boolean values into strings and truncates some integers.
    // Wire DTOs must preserve the declared JSON types instead of silently accepting bad data.
    private class StrictStringAdapter : TypeAdapter<String>() {
        override fun read(reader: JsonReader): String {
            if (reader.peek() != JsonToken.STRING) throw InvalidPayloadException()
            return reader.nextString()
        }
        override fun write(writer: JsonWriter, value: String) { writer.value(value) }
    }
    private class StrictLongAdapter : TypeAdapter<Long>() {
        override fun read(reader: JsonReader): Long {
            if (reader.peek() != JsonToken.NUMBER) throw InvalidPayloadException()
            return reader.nextString().toLongOrNull() ?: throw InvalidPayloadException()
        }
        override fun write(writer: JsonWriter, value: Long) { writer.value(value) }
    }
    private class StrictIntAdapter : TypeAdapter<Int>() {
        override fun read(reader: JsonReader): Int {
            if (reader.peek() != JsonToken.NUMBER) throw InvalidPayloadException()
            return reader.nextString().toIntOrNull() ?: throw InvalidPayloadException()
        }
        override fun write(writer: JsonWriter, value: Int) { writer.value(value) }
    }
    private class StrictBooleanAdapter : TypeAdapter<Boolean>() {
        override fun read(reader: JsonReader): Boolean {
            if (reader.peek() != JsonToken.BOOLEAN) throw InvalidPayloadException()
            return reader.nextBoolean()
        }
        override fun write(writer: JsonWriter, value: Boolean) { writer.value(value) }
    }

    private class DateAdapter : TypeAdapter<LocalDate>() {
        override fun read(reader: JsonReader): LocalDate {
            if (reader.peek() != JsonToken.STRING) throw InvalidPayloadException()
            val value = reader.nextString()
            if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) throw InvalidPayloadException()
            return try { LocalDate.parse(value) }
            catch (_: DateTimeParseException) { throw InvalidPayloadException() }
        }
        override fun write(writer: JsonWriter, value: LocalDate) { writer.value(value.toString()) }
    }

    private class DecimalAdapter : TypeAdapter<BigDecimal>() {
        override fun read(reader: JsonReader): BigDecimal {
            if (reader.peek() != JsonToken.NUMBER) throw InvalidPayloadException()
            return try { BigDecimal(reader.nextString()) }
            catch (_: NumberFormatException) { throw InvalidPayloadException() }
        }
        override fun write(writer: JsonWriter, value: BigDecimal) { writer.value(value) }
    }
}
