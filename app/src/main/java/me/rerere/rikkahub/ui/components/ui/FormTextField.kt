package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    externalKey: Any,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    outputTransformation: OutputTransformation? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
) {
    key(externalKey) {
        val state = rememberTextFieldState(initialText = value)
        val latestOnValueChange by rememberUpdatedState(onValueChange)
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        var syncedExternalValue by remember { mutableStateOf(value) }

        LaunchedEffect(state) {
            snapshotFlow { state.text.toString() }
                .distinctUntilChanged()
                .collect { latestOnValueChange(it) }
        }

        LaunchedEffect(value, focused, state) {
            when (
                val reconciliation = reconcileFormTextField(
                    localText = state.text.toString(),
                    incomingExternalValue = value,
                    syncedExternalValue = syncedExternalValue,
                    focused = focused,
                )
            ) {
                is FormTextFieldReconciliation.AdoptExternal -> {
                    state.setTextAndPlaceCursorAtEnd(reconciliation.value)
                    syncedExternalValue = reconciliation.value
                }

                FormTextFieldReconciliation.KeepLocal -> Unit
                is FormTextFieldReconciliation.MarkClean -> {
                    syncedExternalValue = reconciliation.value
                }
            }
        }

        OutlinedTextField(
            state = state,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label?.let { content ->
                { content() }
            },
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            outputTransformation = outputTransformation,
            keyboardOptions = keyboardOptions,
            lineLimits = if (singleLine) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = maxLines,
                )
            },
            scrollState = scrollState,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
        )
    }
}

internal sealed interface FormTextFieldReconciliation {
    data object KeepLocal : FormTextFieldReconciliation
    data class AdoptExternal(val value: String) : FormTextFieldReconciliation
    data class MarkClean(val value: String) : FormTextFieldReconciliation
}

internal fun reconcileFormTextField(
    localText: String,
    incomingExternalValue: String,
    syncedExternalValue: String,
    focused: Boolean,
): FormTextFieldReconciliation {
    if (incomingExternalValue == localText) {
        return FormTextFieldReconciliation.MarkClean(incomingExternalValue)
    }

    val dirty = localText != syncedExternalValue
    if (!focused && !dirty) {
        return FormTextFieldReconciliation.AdoptExternal(incomingExternalValue)
    }

    // A stale async echo of the user's own edit is different from localText while the field is dirty,
    // so it can only land here. That echo is never written back into the editor buffer.
    return FormTextFieldReconciliation.KeepLocal
}

internal data class FormTextFieldBufferSnapshot(
    val externalKey: Any,
    val localText: String,
    val syncedExternalValue: String,
    val didReset: Boolean = false,
)

internal fun resetFormTextFieldBufferOnKeyChange(
    previous: FormTextFieldBufferSnapshot,
    externalKey: Any,
    value: String,
): FormTextFieldBufferSnapshot {
    return if (previous.externalKey == externalKey) {
        previous.copy(didReset = false)
    } else {
        FormTextFieldBufferSnapshot(
            externalKey = externalKey,
            localText = value,
            syncedExternalValue = value,
            didReset = true,
        )
    }
}
