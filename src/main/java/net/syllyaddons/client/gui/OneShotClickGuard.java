package net.syllyaddons.client.gui;

/** Small, clock-independent state holder for the packet-guard self-test. */
final class OneShotClickGuard {
    private final long timeoutMillis;
    private long armedUntilMillis;

    OneShotClickGuard(long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be positive");
        this.timeoutMillis = timeoutMillis;
    }

    synchronized void arm(long nowMillis) {
        armedUntilMillis = nowMillis > Long.MAX_VALUE - timeoutMillis
                ? Long.MAX_VALUE
                : nowMillis + timeoutMillis;
    }

    synchronized boolean consume(long nowMillis) {
        if (armedUntilMillis <= nowMillis) {
            armedUntilMillis = 0;
            return false;
        }
        armedUntilMillis = 0;
        return true;
    }

    synchronized boolean isArmed(long nowMillis) {
        return armedUntilMillis > nowMillis;
    }
}
