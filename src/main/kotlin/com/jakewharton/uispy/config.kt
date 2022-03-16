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
	val items: List<String>,
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
				items = parseItems(requireNotNull(parseResult.getArray("items")) { "Missing required 'items' array" })
			)
		}

		private fun parseItems(array: TomlArray) = buildList<String> {
			require(array.containsStrings()) { "'items' array must contain only strings" }
			for (i in 0 until array.size()) {
				add(array.getString(i))
			}
		}
	}
}
