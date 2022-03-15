package com.jakewharton.uispy

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface HealthCheck {
	suspend fun notifyStart()
	suspend fun notifySuccess()
	suspend fun notifyFail()
}

class HttpHealthCheck(
	private val okhttp: OkHttpClient,
	private val checkUrl: HttpUrl,
) : HealthCheck {
	private val startUrl = checkUrl.newBuilder().addPathSegment("start").build()
	private val failUrl = checkUrl.newBuilder().addPathSegment("start").build()

	override suspend fun notifyStart() = notify(startUrl)
	override suspend fun notifySuccess() = notify(checkUrl)
	override suspend fun notifyFail() = notify(failUrl)

	private suspend fun notify(url: HttpUrl) {
		try {
			okhttp.newCall(Request.Builder().url(url).build()).await()
		} catch (e: HttpException) {
			e.printStackTrace()
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}
}

object NullHealthCheck : HealthCheck {
	override suspend fun notifyStart() {}
	override suspend fun notifySuccess() {}
	override suspend fun notifyFail() {}
}
