@file:JvmName("Main")

package com.jakewharton.uispy

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC

fun main(vararg args: String) {
	UiSpyCommand().main(args)
}

private class UiSpyCommand : CliktCommand(name = "ui-spy") {
	private val debug by option(hidden = true)
		.switch<Debug>(mapOf("--debug" to Debug.Console))
		.default(Debug.Disabled)

	private val checkInterval by option("--interval", metavar = "DURATION")
		.help("Amount of time between checks in ISO8601 duration format (default 1 minute)")
		.convert { Duration.parseIsoString(it) }
		.default(1.minutes)

	private val ifttt by option("--ifttt", metavar = "URL")
		.help("IFTTT webhook URL to trigger (see https://ifttt.com/maker_webhooks)")
		.convert { it.toHttpUrl() }

	private val healthCheckHost by option("--hc-host", metavar = "URL")
		.help("Alternate host for health check notification")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())

	private val healthCheckId by option("--hc", metavar = "ID")
		.help("ID of https://healthchecks.io/ to notify")

	private val handles by argument(name = "IDs")
		.help("Product IDs to watch for availability")
		.multiple(required = true)

	override fun run() = runBlocking {
		val okhttp = OkHttpClient.Builder()
			.apply {
				if (debug.enabled) {
					addNetworkInterceptor(HttpLoggingInterceptor(debug::log).setLevel(BASIC))
				}
			}
			.build()

		val healthCheck = healthCheckId?.let { healthCheckId ->
			HttpHealthCheck(
				okhttp = okhttp,
				checkUrl = healthCheckHost.newBuilder().addPathSegment(healthCheckId).build(),
			)
		} ?: NullHealthCheck

		val notifier = buildList {
			add(ConsoleProductNotifier)
			ifttt?.let { ifttt ->
				add(IftttProductNotifier(okhttp, ifttt))
			}
		}.flatten()

		val json = Json {
			ignoreUnknownKeys = true
		}

		suspend fun loadProducts(url: String): List<Product> {
			val productsJson = okhttp.newCall(Request.Builder().url(url).build()).await()
			val allProducts = json.decodeFromString(ProductsContainer.serializer(), productsJson)
			return allProducts.products
		}

		try {
			var available: Boolean? = null

			while (true) {
				healthCheck.notifyStart()

				var success = false
				try {
					val allProducts = listOf(
						async { loadProducts("https://store.ui.com/collections/unifi-network-unifi-os-consoles/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-network-switching/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-network-routing-offload/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-network-wireless/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-protect/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-door-access/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-accessories/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-connect/products.json") },
						async { loadProducts("https://store.ui.com/collections/unifi-phone-system/products.json") },
						async { loadProducts("https://store.ui.com/collections/operator-airmax-and-ltu/products.json") },
						async { loadProducts("https://store.ui.com/collections/operator-isp-infrastructure/products.json") },
						async { loadProducts("https://store.ui.com/collections/early-access/products.json") },
					).awaitAll().flatten().associateBy { it.handle }

					val variants = handles.mapNotNull { allProducts[it]?.variants?.first() }
					debug.log(variants.toString())

					val allVariantsAvailable = variants.all { it.available }
					if (allVariantsAvailable != available) {
						available = allVariantsAvailable
						notifier.notify("Items", allVariantsAvailable, null)
					}

					success = true
				} finally {
					if (success) {
						healthCheck.notifySuccess()
					} else {
						healthCheck.notifyFail()
					}
				}

				delay(checkInterval)
			}
		} finally {
			okhttp.dispatcher.executorService.shutdown()
			okhttp.connectionPool.evictAll()
		}
	}
}
