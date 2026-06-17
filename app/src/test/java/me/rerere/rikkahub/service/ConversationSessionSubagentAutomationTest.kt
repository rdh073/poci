package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.rerere.automation.cap.Capability
import me.rerere.automation.cap.CapabilityGuard
import me.rerere.automation.cap.Lease
import me.rerere.automation.cap.Surface
import me.rerere.automation.cap.TrustClock
import me.rerere.automation.cap.Verb
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Option B (subagent UI automation): a no-automation PARENT can spawn a subagent that mints its own
 * automation lease. The subagent guard is NOT the session's [ConversationSession.activeAutomationGuard],
 * so the session must track it separately or the kill-switch sweep (which fires on
 * [ConversationSession.hasActiveAutomation]) would miss it — leaving a spawned subagent un-revokable.
 */
class ConversationSessionSubagentAutomationTest {

    private fun session(): ConversationSession = ConversationSession(
        id = Uuid.random(),
        initial = Conversation.ofId(id = Uuid.random()),
        scope = CoroutineScope(Dispatchers.Unconfined),
        onIdle = {},
    )

    private fun guard(): CapabilityGuard = CapabilityGuard(
        capability = Capability.root(
            sessionId = "sub",
            surface = Surface.Scoped(setOf("com.example.app")),
            verbs = setOf(Verb.OBSERVE),
            lease = Lease(expiresAt = Long.MAX_VALUE, maxSteps = 100),
        ),
        clock = TrustClock { 0L },
    )

    @Test
    fun `hasActiveAutomation tracks the subagent guard set`() {
        val s = session()
        assertFalse("a fresh session has no active automation", s.hasActiveAutomation())

        val g = guard()
        s.addSubagentAutomationGuard(g)
        assertTrue("a registered subagent guard counts as active automation", s.hasActiveAutomation())

        s.removeSubagentAutomationGuard(g)
        assertFalse("removing the last subagent guard clears active automation", s.hasActiveAutomation())
    }

    @Test
    fun `revokeAutomation revokes a subagent guard even with no main guard`() {
        // The exact no-automation-parent case: the session has NO activeAutomationGuard, only a
        // spawned subagent's guard. The kill switch must still revoke it.
        val s = session()
        val g = guard()
        s.addSubagentAutomationGuard(g)
        assertFalse(g.isRevoked)

        s.revokeAutomation()

        assertTrue("the kill switch must revoke a subagent guard with no main lease", g.isRevoked)
    }

    @Test
    fun `revokeAutomation revokes both the main and subagent guards`() {
        val s = session()
        val main = guard()
        val sub = guard()
        s.activeAutomationGuard = main
        s.addSubagentAutomationGuard(sub)

        s.revokeAutomation()

        assertTrue("the main lease guard must be revoked", main.isRevoked)
        assertTrue("the subagent lease guard must be revoked", sub.isRevoked)
    }
}
