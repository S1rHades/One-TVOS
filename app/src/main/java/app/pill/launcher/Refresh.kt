package app.pill.launcher

import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wakes the data loops early. Each loop waits its interval between fetches, but timers
 * stand still while the TV sleeps, and a fetch that failed for want of a connection
 * shouldn't sit out its retry delay once the connection is back. A nudge (the TV woke,
 * the launcher came back to the front, the internet returned) ends a wait that is over by
 * the clock, or one that follows a failure; any other wait carries on, so a nudge never
 * makes a loop fetch more often than its interval.
 */
class Nudges(
    /** A clock that keeps counting while the TV sleeps; replaceable for tests. */
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun nudge() {
        flow.tryEmit(Unit)
    }

    /** Waits [ms]; a nudge ends the wait once [ms] have passed by the clock or, after a failure ([failed]), at once. */
    suspend fun wait(ms: Long, failed: Boolean = false) {
        val due = now() + ms
        withTimeoutOrNull(ms) { flow.first { failed || now() >= due } }
    }
}
