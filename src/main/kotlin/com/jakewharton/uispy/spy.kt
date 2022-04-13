package com.jakewharton.uispy

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class UiSpy(
	private val okHttp: OkHttpClient,
	private val config: Config,
	private val notifier: ProductNotifier,
	private val database: Database,
	private val debug: Debug,
) {
	suspend fun check() = coroutineScope {
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
	}

	private suspend fun loadProducts(url: String): List<Product> {
		val storeUrl = config.store.resolve(url)!!
		val productsJson = okHttp.newCall(Request.Builder().url(storeUrl).build()).await()
		val allProducts = json.decodeFromString(ProductsContainer.serializer(), productsJson)
		return allProducts.products
	}

	private companion object {
		private val json = Json {
			ignoreUnknownKeys = true
		}
	}
}
