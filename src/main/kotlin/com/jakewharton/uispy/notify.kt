package com.jakewharton.uispy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

interface ProductNotifier {
	suspend fun notify(url: HttpUrl, name: String, available: Boolean)
}

fun List<ProductNotifier>.flatten(): ProductNotifier {
	return if (size == 1) get(0) else CompositeProductNotifier(this)
}

private class CompositeProductNotifier(
	private val productNotifiers: List<ProductNotifier>,
) : ProductNotifier {
	override suspend fun notify(url: HttpUrl, name: String, available: Boolean) {
		for (productNotifier in productNotifiers) {
			productNotifier.notify(url, name, available)
		}
	}
}

object ConsoleProductNotifier : ProductNotifier {
	override suspend fun notify(url: HttpUrl, name: String, available: Boolean) {
		if (available) {
			println("AVAILABLE: $name $url")
		} else {
			println("UNAVAILABLE: $name")
		}
	}
}

class IftttProductNotifier(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : ProductNotifier {
	override suspend fun notify(url: HttpUrl, name: String, available: Boolean) {
		val body = PostBody(
			value1 = name,
			value2 = if (available) "Available" else "Unavailable",
			value3 = url.toString(),
		)
		val call = okhttp.newCall(Request.Builder().url(this.url).post(body.toJson()).build())
		call.await()
	}

	@Serializable
	private data class PostBody(
		val value1: String? = null,
		val value2: String? = null,
		val value3: String? = null
	) {
		fun toJson(): RequestBody {
			val json = format.encodeToString(serializer(), this)
			return json.toRequestBody("application/json".toMediaType())
		}

		private companion object {
			private val format = Json {
				encodeDefaults = false
			}
		}
	}
}
