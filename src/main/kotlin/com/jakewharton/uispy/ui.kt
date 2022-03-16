package com.jakewharton.uispy

import kotlinx.serialization.Serializable

@Serializable
data class ProductsContainer(
	val products: List<Product>,
)

@Serializable
data class Product(
	val handle: String,
	val title: String,
	val variants: List<ProductVariant> = emptyList(),
)

@Serializable
data class ProductVariant(
	val product_id: Long,
	val available: Boolean = false,
)
