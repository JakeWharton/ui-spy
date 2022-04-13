@file:JvmName("Main")

package com.jakewharton.uispy

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.io.path.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC

fun main(vararg args: String) {
	UiSpyCommand(FileSystems.getDefault()).main(args)
}

private class UiSpyCommand(fs: FileSystem) : CliktCommand(name = "ui-spy") {
	private val debug by option(hidden = true)
		.switch<Debug>(mapOf("--debug" to Debug.Console))
		.default(Debug.Disabled)

	private val healthCheckHost by option("--hc-host", metavar = "URL", envvar = "HEALTHCHECK_HOST")
		.help("Alternate host for health check notification")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())

	private val healthCheckId by option("--hc", metavar = "ID", envvar = "HEALTHCHECK_ID")
		.help("ID of https://healthchecks.io/ to notify")

	private val config by argument("CONFIG")
		.help("Path to config TOML")
		.path(fileSystem = fs)

	@Suppress("USELESS_CAST") // Needed to keep the type abstract.
	private val database by option("--data", metavar = "PATH")
		.help("Directory into which available products are tracked (default in-memory)")
		.path(canBeFile = false, fileSystem = fs)
		.convert { FileSystemDatabase(it) as Database }
		.defaultLazy { InMemoryDatabase() }

	override fun run() = runBlocking {
		val okHttp = OkHttpClient.Builder()
			.apply {
				if (debug.enabled) {
					addNetworkInterceptor(HttpLoggingInterceptor(debug::log).setLevel(BASIC))
				}
			}
			.build()

		val healthCheck = healthCheckId?.let { healthCheckId ->
			HttpHealthCheck(
				okhttp = okHttp,
				checkUrl = healthCheckHost.newBuilder().addPathSegment(healthCheckId).build(),
			)
		} ?: NullHealthCheck

		val json = Json {
			ignoreUnknownKeys = true
		}

		try {
			while (true) {
				healthCheck.notifyStart()

				val config = Config.parseToml(config.readText())

				val notifier = buildList {
					add(ConsoleProductNotifier)
					if (config.ifttt != null) {
						add(IftttProductNotifier(okHttp, config.ifttt))
					}
				}.flatten()

				val spy = UiSpy(okHttp, config, jsonLinks, notifier, database, debug)

				var success = false
				try {
					spy.check()

					success = true
				} finally {
					if (success) {
						healthCheck.notifySuccess()
					} else {
						healthCheck.notifyFail()
					}
				}

				delay(config.checkInterval)
			}
		} finally {
			okHttp.dispatcher.executorService.shutdown()
			okHttp.connectionPool.evictAll()
		}
	}

	companion object {
		val jsonLinks = listOf(
			"collections/unifi-network-unifi-os-consoles/products.json",
			"collections/unifi-network-switching/products.json",
			"collections/unifi-network-routing-offload/products.json",
			"collections/unifi-network-wireless/products.json",
			"collections/unifi-protect/products.json",
			"collections/unifi-door-access/products.json",
			"collections/unifi-accessories/products.json",
			"collections/unifi-connect/products.json",
			"collections/unifi-phone-system/products.json",
			"collections/operator-airmax-and-ltu/products.json",
			"collections/operator-isp-infrastructure/products.json",
			"collections/early-access/products.json",
		)
	}
}
