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
		assertFalse(database.isProductAvailable(123, null))
		assertFalse(database.isProductAvailable(456, 789))
	}

	@Test fun availabilityReflectsChangeNoVariant() {
		assertFalse(database.isProductAvailable(123, null))

		database.productAvailabilityChange(123, null, true)
		assertTrue(database.isProductAvailable(123, null))

		database.productAvailabilityChange(123, null, false)
		assertFalse(database.isProductAvailable(123, null))
	}

	@Test fun availabilityReflectsChangeWithVariant() {
		assertFalse(database.isProductAvailable(123, 456))

		// Changing a different variant which should not affect the one we want.
		database.productAvailabilityChange(123, 789, true)
		assertFalse(database.isProductAvailable(123, 456))

		database.productAvailabilityChange(123, 456, true)
		assertTrue(database.isProductAvailable(123, 456))
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
