package com.dinuka.hutchwatchdog

import org.junit.Assert.assertEquals
import org.junit.Test

class StallStateMachineTest {
    private var now = 1_000L
    private val machine = StallStateMachine { now }

    @Test
    fun firstFailureBecomesSuspicious() {
        machine.start()
        val snapshot = machine.onProbe(ProbeResult(dnsOk = false, httpsOk = false, message = "failed"))

        assertEquals(WatchdogState.SUSPICIOUS, snapshot.state)
        assertEquals(1, snapshot.consecutiveFailures)
    }

    @Test
    fun secondFailureBecomesStalled() {
        machine.start()
        machine.onProbe(ProbeResult(dnsOk = false, httpsOk = false))
        val snapshot = machine.onProbe(ProbeResult(dnsOk = true, httpsOk = false))

        assertEquals(WatchdogState.STALLED, snapshot.state)
        assertEquals(2, snapshot.consecutiveFailures)
    }

    @Test
    fun successClearsFailureState() {
        machine.start()
        machine.onProbe(ProbeResult(dnsOk = false, httpsOk = false))
        now = 2_000L
        val snapshot = machine.onProbe(ProbeResult(dnsOk = true, httpsOk = true))

        assertEquals(WatchdogState.HEALTHY, snapshot.state)
        assertEquals(0, snapshot.consecutiveFailures)
        assertEquals(2_000L, snapshot.lastSuccessAtMs)
    }

    @Test
    fun recoveryRequiresTwoSuccessesBeforeHealthy() {
        machine.start()
        machine.onProbe(ProbeResult(dnsOk = false, httpsOk = false))
        machine.onProbe(ProbeResult(dnsOk = false, httpsOk = false))
        machine.beginRecovery()

        val first = machine.onProbe(ProbeResult(dnsOk = true, httpsOk = true))
        val second = machine.onProbe(ProbeResult(dnsOk = true, httpsOk = true))

        assertEquals(WatchdogState.RECOVERING, first.state)
        assertEquals(WatchdogState.HEALTHY, second.state)
    }

    @Test
    fun customProbeFailureCountsAsFailure() {
        machine.start()
        val snapshot = machine.onProbe(ProbeResult(dnsOk = true, httpsOk = true, customOk = false))

        assertEquals(WatchdogState.SUSPICIOUS, snapshot.state)
    }
}
