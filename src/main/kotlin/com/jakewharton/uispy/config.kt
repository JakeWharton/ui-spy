package com.jakewharton.uispy

import com.akuleshov7.ktoml.Toml
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class Config(
	@Serializable(HttpUrlSerializer::class)
	val ifttt: HttpUrl? = null,
	@Serializable(IsoDurationSerializer::class)
	val checkInterval: Duration = 1.minutes,
	@Serializable(HttpUrlSerializer::class)
	val store: HttpUrl = "https://store.ui.com".toHttpUrl(),
	val items: List<String>,
) {
	companion object {
		private val serializer = Toml

		fun parseToml(toml: String): Config {
			return serializer.decodeFromString(serializer(), toml)
		}
	}
}

private object IsoDurationSerializer : KSerializer<Duration> {
	override val descriptor = PrimitiveSerialDescriptor("kotlin.time.Duration", STRING)

	override fun deserialize(decoder: Decoder): Duration {
		return Duration.parseIsoString(decoder.decodeString())
	}

	override fun serialize(encoder: Encoder, value: Duration) {
		throw UnsupportedOperationException()
	}
}

private object HttpUrlSerializer : KSerializer<HttpUrl> {
	override val descriptor = PrimitiveSerialDescriptor("okhttp3.HttpUrl", STRING)

	override fun deserialize(decoder: Decoder): HttpUrl {
		return decoder.decodeString().toHttpUrl()
	}

	override fun serialize(encoder: Encoder, value: HttpUrl) {
		throw UnsupportedOperationException()
	}
}
