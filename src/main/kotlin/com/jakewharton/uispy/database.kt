package com.jakewharton.uispy

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.useDirectoryEntries
import kotlin.io.path.writeText

interface Database {
	fun allProducts(): Set<Long>
	fun getProductAvailability(productId: Long): ProductAvailability
	fun updateProductAvailability(productId: Long, availability: ProductAvailability)
	fun removeProduct(productId: Long)
}

data class ProductAvailability(
	/* fileprivate */ val variantAvailability: Map<Long, Boolean>,
) {
	fun anyVariantAvailable() = variantAvailability.values.any { it }
	fun isVariantAvailable(variantId: Long) = variantAvailability[variantId] ?: false

	companion object {
		val None = ProductAvailability(emptyMap())
	}
}

class InMemoryDatabase : Database {
	private val productAvailabilities = mutableMapOf<Long, ProductAvailability>()
	override fun allProducts() = productAvailabilities.keys
	override fun getProductAvailability(productId: Long) = productAvailabilities[productId] ?: ProductAvailability.None

	override fun updateProductAvailability(productId: Long, availability: ProductAvailability) {
		productAvailabilities[productId] = availability
	}

	override fun removeProduct(productId: Long) {
		requireNotNull(productAvailabilities.remove(productId)) {
			"Unknown product ID $productId"
		}
	}
}

class FileSystemDatabase(private val root: Path) : Database {
	/*
	Structure:

	root/
	 +-- 1234/
	 |    +-- 567 "true"
	 |    `-- 678 "false"
	 `-- 2345/
	      +-- 678 "false"
	      `-- 789 "true"
	*/
	init {
		root.createDirectories()
	}

	override fun allProducts(): Set<Long> {
		return root.listDirectoryEntries()
			.mapTo(mutableSetOf()) { it.name.toLong() }
	}

	override fun getProductAvailability(productId: Long): ProductAvailability {
		val productPath = root.resolve(productId.toString())
		if (productPath.notExists()) {
			return ProductAvailability.None
		}
		val availability = productPath
			.listDirectoryEntries()
			.associate { it.name.toLong() to it.readText().toBoolean() }
		return ProductAvailability(availability)
	}

	override fun updateProductAvailability(productId: Long, availability: ProductAvailability) {
		val productPath = root.resolve(productId.toString())
		productPath.createDirectories()

		val removedVariants = productPath
			.listDirectoryEntries()
			.mapTo(mutableSetOf()) { it.name.toLong() }
			.minus(availability.variantAvailability.keys)
		for (removedVariant in removedVariants) {
			productPath.resolve(removedVariant.toString()).deleteExisting()
		}

		for ((variantId, isAvailable) in availability.variantAvailability) {
			productPath.resolve(variantId.toString()).writeText(isAvailable.toString())
		}
	}

	override fun removeProduct(productId: Long) {
		root.resolve(productId.toString()).apply {
			useDirectoryEntries {
				for (entry in it) {
					entry.deleteExisting()
				}
			}
			deleteExisting()
		}
	}
}
