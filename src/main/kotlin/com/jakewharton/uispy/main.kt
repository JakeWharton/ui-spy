@file:JvmName("Main")

package com.jakewharton.uispy

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.io.path.readText
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
	UiSpyCommand(FileSystems.getDefault()).main(args)
}

private class UiSpyCommand(fs: FileSystem) : CliktCommand(name = "ui-spy") {
	private val debug by option(hidden = true)
		.switch<Debug>(mapOf("--debug" to Debug.Console))
		.default(Debug.Disabled)

	private val healthCheckHost by option("--hc-host", metavar = "URL", envvar = "HEALTHCHECK_HOST")
		.help("Alternate host for health check notification")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())

	private val healthCheckId by option("--hc", metavar = "ID", envvar = "HEALTHCHECK_ID")
		.help("ID of https://healthchecks.io/ to notify")

	private val config by argument("CONFIG")
		.help("Path to config TOML")
		.path(fileSystem = fs)

	@Suppress("USELESS_CAST") // Needed to keep the type abstract.
	private val database by option("--data", metavar = "PATH")
		.help("Directory into which available products are tracked (default in-memory)")
		.path(canBeFile = false, fileSystem = fs)
		.convert { FileSystemDatabase(it) as Database }
		.defaultLazy { InMemoryDatabase() }

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

		val json = Json {
			ignoreUnknownKeys = true
		}

		try {
			while (true) {
				healthCheck.notifyStart()

				val config = Config.parseToml(config.readText())

				val notifier = buildList {
					add(ConsoleProductNotifier)
					if (config.ifttt != null) {
						add(IftttProductNotifier(okhttp, config.ifttt))
					}
				}.flatten()

				suspend fun loadProducts(url: String): List<Product> {
					val storeUrl = config.store.resolve(url)!!
					val productsJson = okhttp.newCall(Request.Builder().url(storeUrl).build()).await()
					val allProducts = json.decodeFromString(ProductsContainer.serializer(), productsJson)
					return allProducts.products
				}

				var success = false
				try {
					val products = listOf(
						async { loadProducts("collections/unifi-network-unifi-os-consoles/products.json") },
						async { loadProducts("collections/unifi-network-switching/products.json") },
						async { loadProducts("collections/unifi-network-routing-offload/products.json") },
						async { loadProducts("collections/unifi-network-wireless/products.json") },
						async { loadProducts("collections/unifi-protect/products.json") },
						async { loadProducts("collections/unifi-door-access/products.json") },
						async { loadProducts("collections/unifi-accessories/products.json") },
						async { loadProducts("collections/unifi-connect/products.json") },
						async { loadProducts("collections/unifi-phone-system/products.json") },
						async { loadProducts("collections/operator-airmax-and-ltu/products.json") },
						async { loadProducts("collections/operator-isp-infrastructure/products.json") },
						async { loadProducts("collections/early-access/products.json") },
					).awaitAll().flatten().associateBy { it.handle }

					for (productVariant in config.productVariants) {
						val product = products[productVariant.handle]
						if (product == null) {
							println("WARNING: No product '${productVariant.handle}'")
							continue
						}
						val productAvailability = database.getProductAvailability(product.id)
						val lastAvailability = if (productVariant.variantId == null) {
							productAvailability.anyVariantAvailable()
						} else {
							productAvailability.isVariantAvailable(productVariant.variantId)
						}

						val variant = if (productVariant.variantId != null) {
							val variant = product.variants.firstOrNull { it.id == productVariant.variantId }
							if (variant == null) {
								println("WARNING: No variant ${productVariant.variantId} for product '${productVariant.handle}'")
							}
							variant
						} else {
							null
						}
						val thisAvailability = variant?.available ?: product.variants.any { it.available }

						debug.log("[$productVariant] last:$lastAvailability this:$thisAvailability")

						if (lastAvailability != thisAvailability) {
							val url = config.store.newBuilder()
								.addPathSegment("products")
								.addPathSegment(product.handle)
								.apply {
									if (variant != null) {
										addQueryParameter("variant", variant.id.toString())
									}
								}
								.build()

							val name = if (variant != null) {
								"${product.title} [${variant.title}]"
							} else {
								product.title
							}

							notifier.availabilityChange(url, name, thisAvailability)
						}
					}

					// Notify of new products and clean up removed products (only if this isn't first run).
					val knownProductIds = database.allProducts()
					if (knownProductIds.isNotEmpty()) {
						val activeProductIds = products.values.associateBy { it.id }

						for (addedProductId in activeProductIds.keys - knownProductIds) {
							val addedProduct = activeProductIds.getValue(addedProductId)
							val url = config.store.newBuilder()
								.addPathSegment("products")
								.addPathSegment(addedProduct.handle)
								.build()
							notifier.added(url, addedProduct.title, addedProduct.variants.any { it.available })
						}

						for (removedProductId in knownProductIds - activeProductIds.keys) {
							database.removeProduct(removedProductId)
						}
					}

					for (product in products.values) {
						val availability =
							ProductAvailability(product.variants.associate { it.id to it.available })
						database.updateProductAvailability(product.id, availability)
					}

					success = true
				} finally {
					if (success) {
						healthCheck.notifySuccess()
					} else {
						healthCheck.notifyFail()
					}
				}

				delay(config.checkInterval)
			}
		} finally {
			okhttp.dispatcher.executorService.shutdown()
			okhttp.connectionPool.evictAll()
		}
	}
}
