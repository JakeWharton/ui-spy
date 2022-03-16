package com.jakewharton.uispy

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class ConfigTest {
	@Test fun itemsRequired() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("")
		}
		assertThat(t).hasMessageThat().contains("Missing required 'items' array")
	}

	@Test fun itemsOneLine() {
		val config = Config.parseToml(
			"""
				|items = [ "hey", "there", "bud" ]
				|""".trimMargin()
		)
		assertThat(config.items).containsExactly("hey", "there", "bud")
	}

	@Test fun itemsMultilineLine() {
		val config = Config.parseToml(
			"""
				|items = [
				|  "hey",
				|  "there",
				|  "bud",
				|]
				|""".trimMargin()
		)
		assertThat(config.items).containsExactly("hey", "there", "bud")
	}

	@Test fun itemsMustBeStrings() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml(
				"""
				|items = [ 1, 2, 3 ]
				|""".trimMargin()
			)
		}
		assertThat(t).hasMessageThat().isEqualTo("'items' array must contain only strings")
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

	@Test fun storeValidUrl() {
		val config = Config.parseToml("""
			|store = "https://example.com/stuff"
			|items = []
			|""".trimMargin())
		assertThat(config.store).isEqualTo("https://example.com/stuff".toHttpUrl())
	}

	@Test fun storeInvalidUrl() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("""
				|store = "hello there!"
				|items = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Expected URL scheme 'http' or 'https' but no colon was found")
	}
}
