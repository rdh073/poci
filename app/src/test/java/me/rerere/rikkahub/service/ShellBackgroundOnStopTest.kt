package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STOP_IS_DETACH_NOT_KILL finalizer guard (issue #291). A user stop during a `workspace_shell`
 * foreground wait BACKGROUNDS the run — the coordinator persisted DETACHED under NonCancellable and
 * launched a detached awaiter on AppScope, and the completion arrives later as a synthetic #290
 * event. So the turn finalizer ([ChatService.cancelToolByUser] -> [finishInterruptedPendingToolsForNewSend])
 * must NOT stamp `{status:cancelled}` over that still-pending shell tool part. The pure predicate
 * [shouldBackgroundShellOnStop] is the narrow seam the finalizer consults; pinning it pins "a
 * backgrounded shell is left alone while every other interrupted tool is still cancelled".
 *
 * FAIL-BEFORE: on the unfixed code the predicate does not exist and the finalizer cancelled EVERY
 * not-yet-executed tool, including a backgrounded workspace_shell — so the user would see a
 * `cancelled` result for a run that is still alive (and would then receive a contradictory
 * completion event). After the fix only a pending workspace_shell is spared.
 */
class ShellBackgroundOnStopTest {

    private fun tool(toolName: String, executed: Boolean) = UIMessagePart.Tool(
        toolCallId = "call_1",
        toolName = toolName,
        input = "{}",
        output = if (executed) listOf(UIMessagePart.Text("{}")) else emptyList(),
    )

    // A pending (not-yet-executed) workspace_shell is the backgrounded case: leave it alone.
    @Test
    fun `pending workspace_shell is backgrounded on stop`() {
        assertTrue(shouldBackgroundShellOnStop(tool("workspace_shell", executed = false)))
    }

    // An already-executed workspace_shell (exited inline or killed -> it has output) is NOT pending,
    // so the finalizer never touches it anyway; the predicate must say "do not special-case it".
    @Test
    fun `executed workspace_shell is not a background case`() {
        assertFalse(shouldBackgroundShellOnStop(tool("workspace_shell", executed = true)))
    }

    // Every OTHER interrupted tool is still finalized as cancelled — the guard is shell-only.
    @Test
    fun `other interrupted tools are still cancelled`() {
        assertFalse(shouldBackgroundShellOnStop(tool("ui_set_text", executed = false)))
        assertFalse(shouldBackgroundShellOnStop(tool("workspace_write_file", executed = false)))
        assertFalse(shouldBackgroundShellOnStop(tool("web_search", executed = false)))
    }
}
