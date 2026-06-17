package me.rerere.rikkahub.ui.pages.setting.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.ai.runtime.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.theme.extendColors
import org.koin.compose.koinInject

@Composable
internal fun McpServerConfigModal(state: EditState<McpServerConfig>) {
    val mcpManager = koinInject<McpManager>()
    val settingsStore = koinInject<SettingsStore>()

    state.EditStateContent { config, updateValue ->
        val pagerState = rememberPagerState { 2 }
        val scope = rememberCoroutineScope()

        // Live connection status for THIS server (keyed by id so the flow isn't restarted on every
        // field edit). Drives the Connect/Reconnect button below.
        val status by remember(config.id) { mcpManager.getStatus(config) }
            .collectAsStateWithLifecycle(initialValue = McpStatus.Idle)

        // Persist the draft to settings WITHOUT dismissing the sheet, upserting by id (the first save
        // of a new server appends it, later saves update it). Awaited so a follow-up connect's sync()
        // can find the server by id and write its discovered tools back.
        suspend fun persist(cfg: McpServerConfig) {
            settingsStore.update { s ->
                val exists = s.mcpServers.any { it.id == cfg.id }
                s.copy(
                    mcpServers = if (exists) {
                        s.mcpServers.map { if (it.id == cfg.id) cfg else it }
                    } else {
                        s.mcpServers + cfg
                    }
                )
            }
        }

        ModalBottomSheet(
            onDismissRequest = { state.dismiss() },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.setting_mcp_page_basic_settings)) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.setting_mcp_page_tools)) }
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> McpCommonOptionsConfigure(config = config, update = updateValue)
                        1 -> McpToolsConfigure(config = config, update = updateValue)
                    }
                }

                ConnectionStatusRow(status)

                val nameValid = config.commonOptions.name.isNotBlank()
                val busy = status is McpStatus.Connecting || status is McpStatus.Reconnecting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connect when never connected; Reconnect once it has been tried (error/connected).
                    TextButton(
                        enabled = nameValid && !busy,
                        onClick = {
                            scope.launch {
                                // Persist first so sync() can locate the server by id, then connect.
                                persist(config)
                                mcpManager.addClient(config)
                                // Adopt the tools sync() just wrote to settings so the Tools tab shows
                                // them without reopening the sheet.
                                settingsStore.settingsFlow.value.mcpServers
                                    .find { it.id == config.id }
                                    ?.let { synced ->
                                        updateValue(
                                            config.clone(
                                                commonOptions = config.commonOptions.copy(
                                                    tools = synced.commonOptions.tools
                                                )
                                            )
                                        )
                                    }
                            }
                        }
                    ) {
                        Text(if (status is McpStatus.Idle) "Connect" else "Reconnect")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { state.dismiss() }) {
                            Text("Close")
                        }
                        // Save persists but keeps the sheet open, so the user can switch to the Tools
                        // tab and review/configure the discovered tools before leaving.
                        TextButton(
                            enabled = nameValid,
                            onClick = { scope.launch { persist(config) } }
                        ) {
                            Text(stringResource(R.string.setting_mcp_page_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: McpStatus) {
    val color = when (status) {
        is McpStatus.Connected -> MaterialTheme.extendColors.green6
        is McpStatus.Error -> MaterialTheme.extendColors.red6
        is McpStatus.Connecting, is McpStatus.Reconnecting -> MaterialTheme.colorScheme.primary
        is McpStatus.Idle -> MaterialTheme.colorScheme.outline
    }
    val text = when (status) {
        is McpStatus.Connected -> "Connected"
        is McpStatus.Connecting -> "Connecting…"
        is McpStatus.Reconnecting -> "Reconnecting ${status.attempt}/${status.maxAttempts}…"
        is McpStatus.Error -> status.message
        is McpStatus.Idle -> "Not connected"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (status is McpStatus.Connecting || status is McpStatus.Reconnecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
    }
}
