package com.dinuka.hutchwatchdog

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

class NetworkRecoveryController(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())

    fun isCellularInternet(network: Network?): Boolean {
        if (network == null) return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun askAndroidToRevalidate(network: Network?) {
        if (network != null) {
            connectivityManager.reportNetworkConnectivity(network, false)
        } else {
            connectivityManager.reportNetworkConnectivity(null, false)
        }
        requestFreshCellularPath()
    }

    private fun requestFreshCellularPath() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {}
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectivityManager.requestNetwork(request, callback, 10_000)
            } else {
                connectivityManager.requestNetwork(request, callback)
            }
            handler.postDelayed({
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }, 12_000L)
        }
    }
}
