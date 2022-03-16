package com.jakewharton.uispy

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test

class IftttProductNotifierTest {
	@get:Rule val server = MockWebServer()

	@Test fun simple() = runBlocking {
		val serverUrl = server.url("/")
		val notifier = IftttProductNotifier(OkHttpClient(), serverUrl)

		server.enqueue(MockResponse())
		notifier.notify("https://store.ui.com/products/cool-product".toHttpUrl(), "Cool Product", true)

		val request = server.takeRequest()
		assertThat(request.body.readUtf8())
			.isEqualTo("""{"value1":"Cool Product","value2":"Available","value3":"https://store.ui.com/products/cool-product"}""")
		assertThat(request.requestUrl).isEqualTo(serverUrl)
	}
}
