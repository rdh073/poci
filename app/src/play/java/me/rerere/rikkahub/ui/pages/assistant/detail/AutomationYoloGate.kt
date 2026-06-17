package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.model.AutomationGrant

// Play-distributed flavor: YOLO ("bypass all restriction") is PHYSICALLY ABSENT. The unrestricted
// automation surface (every app incl. system UI and the host, auto-confirmed submit taps) is
// sideload-only, mirroring the workspace shell security boundary; this seam stays empty permanently.
// The lease derivation independently never mints a YOLO capability without the danger acknowledgement,
// which this build provides no UI to set — so the play build cannot reach YOLO even via imported state.
@Composable
internal fun AutomationYoloSection(
    @Suppress("UNUSED_PARAMETER") grant: AutomationGrant,
    @Suppress("UNUSED_PARAMETER") yoloAcknowledged: Boolean,
    @Suppress("UNUSED_PARAMETER") onUpdate: (AutomationGrant) -> Unit,
    @Suppress("UNUSED_PARAMETER") onAcknowledge: () -> Unit,
) {
}
