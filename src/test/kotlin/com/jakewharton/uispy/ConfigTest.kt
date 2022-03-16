package com.jakewharton.uispy

import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class ConfigTest {
	@Test fun itemsRequired() {
		val t = assertFailsWith<TomlDecodingException> {
			Config.parseToml("")
		}
		assertThat(t).hasMessageThat().contains("Missing required property <items>")
	}

	@Test fun iftttValidUrl() {
		val config = Config.parseToml("""
			|ifttt = "https://example.com/stuff"
			|items = []
			|""".trimMargin())
		assertThat(config.ifttt).isEqualTo("https://example.com/stuff".toHttpUrl())
	}

	@Test fun iftttInvalidUrl() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("""
				|ifttt = "hello there!"
				|items = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Expected URL scheme 'http' or 'https' but no colon was found")
	}

	@Test fun checkIntervalValidDuration() {
		val config = Config.parseToml("""
			|checkInterval = "PT10S"
			|items = []
			|""".trimMargin())
		assertThat(config.checkInterval).isEqualTo(10.seconds)
	}

	@Test fun checkIntervalInvalidDuration() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("""
				|checkInterval = "hello there!"
				|items = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Invalid ISO duration string format: 'hello there!'.")
	}
}
