package com.jakewharton.uispy

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class DatabaseTest(
	@Suppress("unused") // Used in JUnit runner.
	private val name: String,
	private val database: Database,
) {
	@Test fun defaultAvailabilityIsFalse() {
		assertFalse(database.isProductAvailable("cool-product"))
		assertFalse(database.isProductAvailable("another-one"))
	}

	@Test fun availabilityReflectsChange() {
		assertFalse(database.isProductAvailable("cool-product"))

		database.productAvailabilityChange("cool-product", true)
		assertTrue(database.isProductAvailable("cool-product"))

		database.productAvailabilityChange("cool-product", false)
		assertFalse(database.isProductAvailable("cool-product"))
	}

	companion object {
		@JvmStatic
		@Parameters(name = "{0}")
		fun data() = listOf(
			arrayOf("memory", InMemoryDatabase()),
			arrayOf("fs", FileSystemDatabase(Jimfs.newFileSystem(unix()).rootDirectories.single()))
		)
	}
}
