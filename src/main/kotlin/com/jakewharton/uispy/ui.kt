package com.jakewharton.uispy

import kotlinx.serialization.Serializable

@Serializable
data class ProductsContainer(
	val products: List<Product>,
)

@Serializable
data class Product(
	val id: Long,
	val handle: String,
	val title: String,
	val variants: List<Variant> = emptyList(),
) {
	@Serializable
	data class Variant(
		val id: Long,
		val title: String,
		val available: Boolean = false,
	)
}
