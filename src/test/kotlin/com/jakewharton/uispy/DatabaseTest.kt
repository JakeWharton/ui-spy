package com.jakewharton.uispy

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
	database: () -> Database,
) {
	private val database = database()

	@Test fun allProductsEmpty() {
		assertEquals(emptySet(), database.allProducts())
	}

	@Test fun allProducts() {
		database.updateProductAvailability(123L, ProductAvailability(mapOf(456L to true)))
		database.updateProductAvailability(234L, ProductAvailability(mapOf(567L to false)))
		assertEquals(setOf(123L, 234L), database.allProducts())
	}

	@Test fun availabilityMissingFalse() {
		val product = database.getProductAvailability(123L)
		assertFalse(product.anyVariantAvailable())
		assertFalse(product.isVariantAvailable(456L))
	}

	@Test fun availabilityExplicitFalse() {
		database.updateProductAvailability(123L, ProductAvailability(mapOf(456L to false)))
		val product = database.getProductAvailability(123L)
		assertFalse(product.anyVariantAvailable())
		assertFalse(product.isVariantAvailable(456L))
	}

	@Test fun availabilityExplicitTrue() {
		database.updateProductAvailability(123L, ProductAvailability(mapOf(456L to true)))
		val product = database.getProductAvailability(123L)
		assertTrue(product.anyVariantAvailable())
		assertTrue(product.isVariantAvailable(456L))
	}

	@Test fun removeProduct() {
		database.updateProductAvailability(123L, ProductAvailability(mapOf(456L to true)))
		database.removeProduct(123L)

		assertEquals(emptySet(), database.allProducts())

		database.getProductAvailability(123L).apply {
			assertFalse(anyVariantAvailable())
			assertFalse(isVariantAvailable(456L))
		}
	}

	@Test fun removeInvalidProductThrows() {
		assertFailsWith<Exception> {
			database.removeProduct(123L)
		}
	}

	companion object {
		@JvmStatic
		@Parameters(name = "{0}")
		fun data() = listOf(
			arrayOf("memory", ::InMemoryDatabase),
			arrayOf("fs", {
				FileSystemDatabase(Jimfs.newFileSystem(unix()).rootDirectories.single().resolve("uispy"))
			})
		)
	}
}
