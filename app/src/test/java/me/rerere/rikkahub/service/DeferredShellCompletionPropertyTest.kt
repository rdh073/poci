package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.shellrun.ShellRunToolAnchor
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class DeferredShellCompletionPropertyTest {
    @Test
    fun `deferred shell anchors are discovered from visible tool parts`() {
        val conversationId = Uuid.random()
        val assistantId = Uuid.random()
        val taskId = Uuid.random()
        val tool = UIMessagePart.Tool(
            toolCallId = "call-shell",
            toolName = "workspace_shell",
            input = """{"command":"sleep 1","detachAfterSeconds":1}""",
            output = listOf(UIMessagePart.Text("""{"taskId":"$taskId","status":"running"}""")),
        ).asDeferred()
        val message = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
        val node = MessageNode(messages = listOf(message))
        val conversation = Conversation(
            id = conversationId,
            assistantId = assistantId,
            messageNodes = listOf(node),
        )

        val anchors = findDeferredShellToolAnchors(conversation)

        assertEquals(1, anchors.size)
        assertEquals(taskId, anchors.single().taskId)
        assertEquals(tool.toolCallId, anchors.single().anchor.toolCallId)
        assertEquals(node.id, anchors.single().anchor.toolNodeId)
        assertEquals(message.id, anchors.single().anchor.toolMessageId)
    }

    @Test
    fun `completion resolves into original tool output and never creates a user message`() {
        val taskId = Uuid.random()
        val tool = UIMessagePart.Tool(
            toolCallId = "call-shell",
            toolName = "workspace_shell",
            input = """{"command":"sleep 1","detachAfterSeconds":1}""",
            output = listOf(UIMessagePart.Text("""{"taskId":"$taskId","status":"running"}""")),
        ).asDeferred()
        val message = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
        val node = MessageNode(messages = listOf(message))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(node),
        )
        val anchor = ShellRunToolAnchor(
            toolCallId = tool.toolCallId,
            toolNodeId = node.id,
            toolMessageId = message.id,
        )
        val payload = """{"taskId":"$taskId","status":"SUCCEEDED","exitCode":0,"tail":"done"}"""

        val first = resolveDeferredShellCompletion(conversation, anchor, payload)
        assertNotNull(first)
        assertTrue(first!!.continueGeneration)
        assertEquals(1, first.conversation.messageNodes.size)
        assertFalse(first.conversation.currentMessages.any { it.role == MessageRole.USER })
        val resolvedTool = first.conversation.currentMessages.single().parts.single() as UIMessagePart.Tool
        assertFalse(resolvedTool.isDeferred)
        assertEquals(payload, (resolvedTool.output.single() as UIMessagePart.Text).text)

        val duplicate = resolveDeferredShellCompletion(first.conversation, anchor, payload)
        assertNotNull(duplicate)
        assertFalse(duplicate!!.continueGeneration)
        val finalTools = duplicate.conversation.currentMessages
            .flatMap { it.parts }
            .filterIsInstance<UIMessagePart.Tool>()
        assertEquals(1, finalTools.count { (it.output.singleOrNull() as? UIMessagePart.Text)?.text == payload })
    }
}
