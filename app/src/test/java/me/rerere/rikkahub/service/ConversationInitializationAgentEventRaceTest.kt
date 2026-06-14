package me.rerere.rikkahub.service

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AGENT_EVENT_ID_METADATA_KEY
import me.rerere.rikkahub.data.model.AGENT_EVENT_KIND_METADATA_KEY
import me.rerere.rikkahub.data.model.AGENT_EVENT_SYNTHETIC_KIND
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.SYNTHETIC_KIND_METADATA_KEY
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Pure regression seam for issue #290's startup replay race. [ChatService.initializeConversation]
 * installs a persisted Room snapshot, while startup replay can concurrently append a synthetic
 * agent-event node and consume the durable row. The initialization write must preserve that live
 * synthetic node; otherwise the consumed event becomes zero-delivery to the model.
 */
class ConversationInitializationAgentEventRaceTest {

    @Test
    fun `initialization snapshot preserves concurrently appended synthetic agent event`() {
        val persisted = conversation(
            message("hello", MessageRole.USER).toMessageNode(),
            message("hi", MessageRole.ASSISTANT).toMessageNode(),
        )
        val synthetic = syntheticAgentEventNode("event-1")
        val liveAfterReplay = persisted.copy(messageNodes = persisted.messageNodes + synthetic)

        val merged = preserveConcurrentSyntheticAgentEventNodes(
            snapshot = persisted,
            live = liveAfterReplay,
        )

        assertEquals(persisted.messageNodes + synthetic, merged.messageNodes)
    }

    @Test
    fun `synthetic node already present in snapshot is not duplicated`() {
        val synthetic = syntheticAgentEventNode("event-1")
        val persisted = conversation(
            message("hello", MessageRole.USER).toMessageNode(),
            synthetic,
        )

        val merged = preserveConcurrentSyntheticAgentEventNodes(
            snapshot = persisted,
            live = persisted,
        )

        assertSame("unchanged snapshots should not allocate a duplicate merge", persisted, merged)
        assertEquals(listOf(synthetic), merged.messageNodes.filter { it == synthetic })
    }

    @Test
    fun `initialization merge does not carry unrelated live nodes`() {
        val persisted = conversation(message("hello", MessageRole.USER).toMessageNode())
        val unrelatedUserNode = message("late user edit", MessageRole.USER).toMessageNode()
        val live = persisted.copy(messageNodes = persisted.messageNodes + unrelatedUserNode)

        val merged = preserveConcurrentSyntheticAgentEventNodes(snapshot = persisted, live = live)

        assertSame(persisted, merged)
    }

    private fun conversation(vararg nodes: MessageNode): Conversation =
        Conversation.ofId(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messages = nodes.toList(),
        )

    private fun message(text: String, role: MessageRole): UIMessage =
        UIMessage(
            role = role,
            parts = listOf(UIMessagePart.Text(text)),
        )

    private fun syntheticAgentEventNode(eventId: String): MessageNode =
        UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Text(
                    text = """{"ok":true}""",
                    metadata = buildJsonObject {
                        put(SYNTHETIC_KIND_METADATA_KEY, AGENT_EVENT_SYNTHETIC_KIND)
                        put(AGENT_EVENT_ID_METADATA_KEY, eventId)
                        put(AGENT_EVENT_KIND_METADATA_KEY, "test")
                    },
                )
            ),
        ).toMessageNode()
}
