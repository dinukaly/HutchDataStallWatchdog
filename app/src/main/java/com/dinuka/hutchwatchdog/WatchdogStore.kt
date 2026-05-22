package com.dinuka.hutchwatchdog

object WatchdogStore {
    private val listeners = linkedSetOf<(WatchdogSnapshot) -> Unit>()

    @Volatile
    var snapshot: WatchdogSnapshot = WatchdogSnapshot()
        private set

    @Synchronized
    fun update(next: WatchdogSnapshot) {
        snapshot = next
        listeners.toList().forEach { it(next) }
    }

    @Synchronized
    fun addListener(listener: (WatchdogSnapshot) -> Unit) {
        listeners += listener
        listener(snapshot)
    }

    @Synchronized
    fun removeListener(listener: (WatchdogSnapshot) -> Unit) {
        listeners -= listener
    }
}
