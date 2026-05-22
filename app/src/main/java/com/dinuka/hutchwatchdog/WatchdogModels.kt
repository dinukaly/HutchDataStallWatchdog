package com.dinuka.hutchwatchdog

enum class WatchdogState {
    OFF,
    HEALTHY,
    SUSPICIOUS,
    STALLED,
    RECOVERING,
    NO_CELLULAR
}

enum class ProbePreset(val label: String, val healthyIntervalMs: Long) {
    BALANCED("Balanced", 60_000L),
    AGGRESSIVE("Aggressive", 30_000L),
    QUIET("Quiet", 180_000L)
}

data class ProbeResult(
    val dnsOk: Boolean,
    val httpsOk: Boolean,
    val customOk: Boolean? = null,
    val message: String = ""
) {
    val success: Boolean
        get() = dnsOk && httpsOk && (customOk != false)
}

data class WatchdogSnapshot(
    val running: Boolean = false,
    val state: WatchdogState = WatchdogState.OFF,
    val lastSuccessAtMs: Long = 0L,
    val recoveryAttempts: Int = 0,
    val consecutiveFailures: Int = 0,
    val consecutiveRecoverySuccesses: Int = 0,
    val lastMessage: String = "Watchdog is off"
)
