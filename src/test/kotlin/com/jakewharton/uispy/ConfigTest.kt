package com.jakewharton.uispy

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.tomlj.TomlInvalidTypeException

class ConfigTest {
	@Test fun productsRequired() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("")
		}
		assertThat(t).hasMessageThat().contains("Missing required 'products' array")
	}

	@Test fun productsOneLine() {
		val config = Config.parseToml(
			"""
				|products = [ "hey", "there", "bud" ]
				|""".trimMargin()
		)
		assertThat(config.productVariants).containsExactly(
			Config.ProductVariant("hey"),
			Config.ProductVariant("there"),
			Config.ProductVariant("bud"),
		)
	}

	@Test fun productsMultilineLine() {
		val config = Config.parseToml(
			"""
				|products = [
				|  "hey",
				|  "there",
				|  "bud",
				|]
				|""".trimMargin()
		)
		assertThat(config.productVariants).containsExactly(
			Config.ProductVariant("hey"),
			Config.ProductVariant("there"),
			Config.ProductVariant("bud"),
		)
	}

	@Test fun productsWithVariants() {
		val config = Config.parseToml(
			"""
				|products = [
				|  "hey",
				|  "there@123",
				|  "bud",
				|]
				|""".trimMargin()
		)
		assertThat(config.productVariants).containsExactly(
			Config.ProductVariant("hey"),
			Config.ProductVariant("there", 123),
			Config.ProductVariant("bud"),
		)
	}

	@Test fun productsMustBeStrings() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml(
				"""
				|products = [ 1, 2, 3 ]
				|""".trimMargin()
			)
		}
		assertThat(t).hasMessageThat().isEqualTo("'products' array must contain only strings")
	}

	@Test fun iftttValidUrl() {
		val config = Config.parseToml("""
			|ifttt = "https://example.com/stuff"
			|products = []
			|""".trimMargin())
		assertThat(config.ifttt).isEqualTo("https://example.com/stuff".toHttpUrl())
	}

	@Test fun iftttInvalidUrl() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("""
				|ifttt = "hello there!"
				|products = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Expected URL scheme 'http' or 'https' but no colon was found")
	}

	@Test fun checkIntervalValidDuration() {
		val config = Config.parseToml("""
			|checkInterval = "PT10S"
			|products = []
			|""".trimMargin())
		assertThat(config.checkInterval).isEqualTo(10.seconds)
	}

	@Test fun checkIntervalInvalidDuration() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("""
				|checkInterval = "hello there!"
				|products = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Invalid ISO duration string format: 'hello there!'.")
	}

	@Test fun storeValidUrl() {
		val config = Config.parseToml("""
			|store = "https://example.com/stuff"
			|products = []
			|""".trimMargin())
		assertThat(config.store).isEqualTo("https://example.com/stuff".toHttpUrl())
	}

	@Test fun storeInvalidUrl() {
		val t = assertFailsWith<IllegalArgumentException> {
			Config.parseToml("""
				|store = "hello there!"
				|products = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Expected URL scheme 'http' or 'https' but no colon was found")
	}

	@Test fun productAddNotificationsValid() {
		val config = Config.parseToml("""
			|productAddNotifications = true
			|products = []
			|""".trimMargin())
		assertThat(config.productAddNotifications).isTrue()
	}

	@Test fun productAddNotificationsInvalid() {
		val t = assertFailsWith<TomlInvalidTypeException> {
			Config.parseToml("""
				|productAddNotifications = "cheese"
				|products = []
				|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("Value of 'productAddNotifications' is a string")
	}
}
