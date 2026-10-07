package com.turnero.app.ui.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_TIME))
    }

    override fun deserialize(decoder: Decoder): LocalTime =
        LocalTime.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_TIME)
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
}
