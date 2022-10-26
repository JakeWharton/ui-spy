package com.jakewharton.uispy

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.tomlj.Toml
import org.tomlj.TomlArray

data class Config(
	val ifttt: HttpUrl? = null,
	val checkInterval: Duration = 1.minutes,
	val store: HttpUrl = "https://store.ui.com".toHttpUrl(),
	val productVariants: List<ProductVariant>,
	val productAddNotifications: Boolean = false,
) {
	companion object {
		fun parseToml(toml: String): Config {
			val parseResult = Toml.parse(toml)
			require(!parseResult.hasErrors()) {
				"Unable to parse TOML config:\n\n * " + parseResult.errors().joinToString("\n *")
			}
			return Config(
				ifttt = parseResult.getString("ifttt")?.toHttpUrl(),
				checkInterval = parseResult.getString("checkInterval")?.let(Duration.Companion::parseIsoString) ?: 1.minutes,
				store = parseResult.getString("store")?.toHttpUrl() ?: "https://store.ui.com".toHttpUrl(),
				productVariants = parseItems(requireNotNull(parseResult.getArray("products")) { "Missing required 'products' array" }),
				productAddNotifications = parseResult.getBoolean("productAddNotifications") ?: false,
			)
		}

		private fun parseItems(array: TomlArray) = buildList {
			for (i in 0 until array.size()) {
				val entry = array.get(i)
				require(entry is String) { "'products' array must contain only strings" }
				require(entry.isNotBlank()) { "Product string must not be blank" }
				val parts = entry.split('@', limit = 2)
				add(ProductVariant(parts[0], parts.getOrNull(1)?.toLong()))
			}
		}
	}

	data class ProductVariant(
		val handle: String,
		val variantId: Long? = null,
	) {
		override fun toString() = if (variantId != null) {
			"$handle@$variantId"
		} else {
			handle
		}
	}
}
