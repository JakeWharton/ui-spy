package com.jakewharton.uispy

import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists

interface Database {
	fun isProductAvailable(handle: String): Boolean
	fun productAvailabilityChange(handle: String, available: Boolean)
}

class InMemoryDatabase : Database {
	private val handles = mutableSetOf<String>()

	override fun isProductAvailable(handle: String) = handle in handles

	override fun productAvailabilityChange(handle: String, available: Boolean) {
		if (available) {
			handles += handle
		} else {
			handles -= handle
		}
	}
}

class FileSystemDatabase(private val root: Path) : Database {
	override fun isProductAvailable(handle: String): Boolean {
		return path(handle).exists()
	}

	override fun productAvailabilityChange(handle: String, available: Boolean) {
		val handlePath = path(handle)
		if (available) {
			handlePath.createFile()
		} else {
			handlePath.deleteExisting()
		}
	}

	private fun path(handle: String) = root.resolve(handle)
}
