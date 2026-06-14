package me.rerere.rikkahub.data.ai.agentevent

import android.util.Log

/**
 * The cold-start replay pass for the agent-event queue (issue #290), composed beside the existing
 * task startup-recovery runner. On a cold start it scans the store for conversations that still
 * hold PENDING events left by a process kill and logs the backlog.
 *
 * What it deliberately does NOT do, and why (the chosen path the proposal asked to document):
 * delivering an event requires a live model continuation, which only `ChatService` can drive
 * against a live session — at cold start there is no such session, and forcing one would either
 * cancel/race a future user turn or start generation the user never asked for, breaking
 * NO_DOUBLE_GENERATION. So replay here is OBSERVE-ONLY: PENDING rows survive in Room (they live in
 * the store, not in memory — SURVIVES_RESTART) and are drained by the same
 * `claimAndAppendAndConsume` path the live turn-end drain uses, the next time that conversation
 * reaches an idle turn-end. The "central race rule" still holds: whichever drain (live turn-end or
 * a future explicit replay) reaches the row first wins the single transactional claim; the other is
 * a no-op. This keeps the v1 posture AT_MOST_ONCE and honest (mirroring [TaskRecoveryRunner]).
 *
 * Failures are swallowed and logged so a recovery hiccup can never block the UI from coming up,
 * exactly the existing startup-recovery posture.
 */
class AgentEventRecoveryRunner(
    private val store: AgentEventStore,
) {
    /**
     * Run the cold-start replay scan once. Returns the number of conversations that still hold
     * PENDING events (for logging/tests); does NOT itself deliver any event — see the class KDoc for
     * why delivery is deferred to the next idle turn-end drain.
     */
    suspend fun runStartupReplay(): Int {
        val pendingConversations = runCatching { store.conversationsWithPending() }
            .onFailure { Log.e(TAG, "agent-event replay scan failed", it) }
            .getOrDefault(emptyList())
        if (pendingConversations.isNotEmpty()) {
            Log.i(
                TAG,
                "agent-event replay: ${pendingConversations.size} conversation(s) hold pending " +
                    "events; deferred to next idle turn-end drain",
            )
        }
        return pendingConversations.size
    }

    private companion object {
        const val TAG = "AgentEventRecovery"
    }
}
