package com.jakewharton.uispy

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists

interface Database {
	fun isProductAvailable(productId: Long, variantId: Long?): Boolean
	fun productAvailabilityChange(productId: Long, variantId: Long?, available: Boolean)
}

class InMemoryDatabase : Database {
	private val keys = mutableSetOf<String>()

	override fun isProductAvailable(productId: Long, variantId: Long?) = "$productId-$variantId" in keys

	override fun productAvailabilityChange(productId: Long, variantId: Long?, available: Boolean) {
		val key = "$productId-$variantId"
		if (available) {
			keys += key
		} else {
			keys -= key
		}
	}
}

class FileSystemDatabase(private val root: Path) : Database {
	init {
		root.createDirectories()
	}

	override fun isProductAvailable(productId: Long, variantId: Long?): Boolean {
		return path(productId, variantId).exists()
	}

	override fun productAvailabilityChange(productId: Long, variantId: Long?, available: Boolean) {
		val handlePath = path(productId, variantId)
		if (available) {
			handlePath.createFile()
		} else {
			handlePath.deleteExisting()
		}
	}

	private fun path(productId: Long, variantId: Long?): Path {
		val file = if (variantId != null) "$productId-$variantId" else "$productId"
		return root.resolve(file)
	}
}
