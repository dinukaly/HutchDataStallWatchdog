package com.dinuka.hutchwatchdog

import android.net.Network
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class ProbeRunner {
    fun run(network: Network?, customUrl: String): ProbeResult {
        val dnsOk = runCatching {
            if (network != null) {
                network.getAllByName(DNS_HOST).isNotEmpty()
            } else {
                InetAddress.getAllByName(DNS_HOST).isNotEmpty()
            }
        }.getOrDefault(false)

        val httpsOk = runCatching {
            probeUrl(network, DEFAULT_PROBE_URL)
        }.getOrDefault(false)

        val customOk = customUrl
            .takeIf { it.isNotBlank() }
            ?.let { url -> runCatching { probeUrl(network, url) }.getOrDefault(false) }

        val failedParts = mutableListOf<String>()
        if (!dnsOk) failedParts += "DNS"
        if (!httpsOk) failedParts += "HTTPS"
        if (customOk == false) failedParts += "custom probe"
        val message = if (failedParts.isEmpty()) {
            "Probe succeeded"
        } else {
            "Failed: ${failedParts.joinToString(", ")}"
        }

        return ProbeResult(dnsOk = dnsOk, httpsOk = httpsOk, customOk = customOk, message = message)
    }

    fun pokeBurst(network: Network?, customUrl: String): Boolean {
        repeat(3) {
            if (run(network, customUrl).success) return true
            Thread.sleep(1_000L)
        }
        return false
    }

    private fun probeUrl(network: Network?, rawUrl: String): Boolean {
        val url = URL(rawUrl)
        val connection = if (network != null) {
            network.openConnection(url)
        } else {
            url.openConnection()
        } as HttpURLConnection

        return connection.use {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            val status = responseCode
            status == 204 || status in 200..399
        }
    }

    private inline fun <T : HttpURLConnection, R> T.use(block: T.() -> R): R {
        return try {
            block()
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val DNS_HOST = "connectivitycheck.gstatic.com"
        private const val DEFAULT_PROBE_URL = "https://connectivitycheck.gstatic.com/generate_204"
    }
}
