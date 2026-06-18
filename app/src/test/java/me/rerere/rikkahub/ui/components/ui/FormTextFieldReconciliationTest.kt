package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormTextFieldReconciliationTest {

    @Test
    fun stale_echo_while_focused_does_not_reset_local_text() {
        val result = reconcileFormTextField(
            localText = "abcd",
            incomingExternalValue = "abc",
            syncedExternalValue = "abc",
            focused = true,
        )

        assertEquals(FormTextFieldReconciliation.KeepLocal, result)
    }

    @Test
    fun blur_before_echo_catches_up_does_not_clobber_dirty_text() {
        val result = reconcileFormTextField(
            localText = "abcd",
            incomingExternalValue = "abc",
            syncedExternalValue = "abc",
            focused = false,
        )

        assertEquals(FormTextFieldReconciliation.KeepLocal, result)
    }

    @Test
    fun external_catch_up_clears_dirty_without_rewrite() {
        val result = reconcileFormTextField(
            localText = "abcd",
            incomingExternalValue = "abcd",
            syncedExternalValue = "abc",
            focused = false,
        )

        assertEquals(FormTextFieldReconciliation.MarkClean("abcd"), result)
    }

    @Test
    fun external_reset_while_unfocused_and_clean_is_adopted() {
        val result = reconcileFormTextField(
            localText = "abc",
            incomingExternalValue = "reset",
            syncedExternalValue = "abc",
            focused = false,
        )

        assertEquals(FormTextFieldReconciliation.AdoptExternal("reset"), result)
    }

    @Test
    fun external_key_change_resets_buffer_immediately() {
        val oldBuffer = FormTextFieldBufferSnapshot(
            externalKey = "assistant-a:name",
            localText = "dirty local",
            syncedExternalValue = "old external",
        )

        val nextBuffer = resetFormTextFieldBufferOnKeyChange(
            previous = oldBuffer,
            externalKey = "assistant-b:name",
            value = "new assistant",
        )

        assertEquals("assistant-b:name", nextBuffer.externalKey)
        assertEquals("new assistant", nextBuffer.localText)
        assertEquals("new assistant", nextBuffer.syncedExternalValue)
        assertTrue(nextBuffer.didReset)
    }

    @Test
    fun unchanged_external_key_keeps_existing_buffer() {
        val oldBuffer = FormTextFieldBufferSnapshot(
            externalKey = "assistant-a:name",
            localText = "dirty local",
            syncedExternalValue = "old external",
        )

        val nextBuffer = resetFormTextFieldBufferOnKeyChange(
            previous = oldBuffer,
            externalKey = "assistant-a:name",
            value = "new external",
        )

        assertEquals(oldBuffer.externalKey, nextBuffer.externalKey)
        assertEquals(oldBuffer.localText, nextBuffer.localText)
        assertEquals(oldBuffer.syncedExternalValue, nextBuffer.syncedExternalValue)
        assertFalse(nextBuffer.didReset)
    }
}
