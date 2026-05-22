package com.dinuka.hutchwatchdog

class StallStateMachine(
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    var snapshot: WatchdogSnapshot = WatchdogSnapshot()
        private set

    fun start(): WatchdogSnapshot {
        snapshot = snapshot.copy(
            running = true,
            state = WatchdogState.HEALTHY,
            lastMessage = "Waiting for first probe"
        )
        return snapshot
    }

    fun stop(): WatchdogSnapshot {
        snapshot = WatchdogSnapshot()
        return snapshot
    }

    fun markNoCellular(): WatchdogSnapshot {
        snapshot = snapshot.copy(
            running = true,
            state = WatchdogState.NO_CELLULAR,
            consecutiveFailures = 0,
            consecutiveRecoverySuccesses = 0,
            lastMessage = "Default network is not cellular internet"
        )
        return snapshot
    }

    fun onProbe(result: ProbeResult): WatchdogSnapshot {
        if (result.success) {
            val recoverySuccesses =
                if (snapshot.state == WatchdogState.RECOVERING) {
                    snapshot.consecutiveRecoverySuccesses + 1
                } else {
                    0
                }
            val state =
                if (snapshot.state == WatchdogState.RECOVERING && recoverySuccesses < 2) {
                    WatchdogState.RECOVERING
                } else {
                    WatchdogState.HEALTHY
                }
            snapshot = snapshot.copy(
                running = true,
                state = state,
                lastSuccessAtMs = nowMs(),
                consecutiveFailures = 0,
                consecutiveRecoverySuccesses = recoverySuccesses,
                lastMessage = if (state == WatchdogState.RECOVERING) {
                    "First recovery probe succeeded"
                } else {
                    "Internet path is healthy"
                }
            )
            return snapshot
        }

        val failures = snapshot.consecutiveFailures + 1
        val nextState = if (failures >= 2) WatchdogState.STALLED else WatchdogState.SUSPICIOUS
        snapshot = snapshot.copy(
            running = true,
            state = nextState,
            consecutiveFailures = failures,
            consecutiveRecoverySuccesses = 0,
            lastMessage = result.message.ifBlank { "Probe failed" }
        )
        return snapshot
    }

    fun beginRecovery(): WatchdogSnapshot {
        snapshot = snapshot.copy(
            running = true,
            state = WatchdogState.RECOVERING,
            recoveryAttempts = snapshot.recoveryAttempts + 1,
            consecutiveRecoverySuccesses = 0,
            lastMessage = "Recovery attempt ${snapshot.recoveryAttempts + 1} started"
        )
        return snapshot
    }
}
