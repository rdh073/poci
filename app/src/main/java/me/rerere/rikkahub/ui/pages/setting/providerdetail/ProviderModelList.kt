package me.rerere.rikkahub.ui.pages.setting.providerdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ConnectionResult
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.ext.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Picks which [ProviderSetting] authenticates the listModels fetch in the Models tab.
 *
 * Uses the live Config-tab [draft] (just-typed apiKey/baseUrl/headers) only while the draft is the
 * same provider type as [persisted]. A pending (unsaved) type change makes the draft a different
 * type than the persisted provider that model-list writes (add/del/edit/move) target; fetching that
 * type's models and persisting them into the old-type provider would cross-contaminate the model
 * list. In that case fall back to the persisted provider until the type change is saved.
 */
internal fun selectModelFetchSetting(
    persisted: ProviderSetting,
    draft: ProviderSetting
): ProviderSetting = if (draft::class == persisted::class) draft else persisted

@Composable
internal fun ModelList(
    providerSetting: ProviderSetting,
    draft: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
    vm: ProviderDetailViewModel = koinViewModel(),
) {
    val fetchSetting = selectModelFetchSetting(providerSetting, draft)
    val catalogState by vm.catalog.collectAsStateWithLifecycle()

    // Fetch the catalog through the ViewModel so a failed fetch becomes an explicit, classified
    // ModelCatalogState.Failed (the user sees WHY) instead of produceState(emptyList())'s silent
    // empty list — failure and empty success used to be indistinguishable. Keyed on fetchSetting so
    // a saved config change re-fetches; the VM cancels any superseded fetch.
    LaunchedEffect(fetchSetting) {
        vm.refreshCatalog(fetchSetting)
    }

    val modelList = (catalogState as? ModelCatalogState.Loaded)?.models
        ?.sortedBy { it.modelId }
        .orEmpty()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(providerSetting.moveMove(from.index, to.index))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                ),
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            // 模型列表
            if (providerSetting.models.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        when (val state = catalogState) {
                            is ModelCatalogState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Text(
                                    text = "Fetching models…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is ModelCatalogState.Failed -> {
                                Text(
                                    text = connectionResultTitle(state.result),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = connectionResultHint(state.result),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            else -> {
                                Text(
                                    text = stringResource(R.string.setting_provider_page_no_models),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.setting_provider_page_add_models_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(providerSetting.models, key = { it.id }) { item ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = item.id
                    ) { isDragging ->
                        ModelCard(
                            model = item,
                            onDelete = {
                                onUpdateProvider(providerSetting.delModel(item))
                            },
                            onEdit = { editedModel ->
                                onUpdateProvider(providerSetting.editModel(editedModel))
                            },
                            parentProvider = providerSetting,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .graphicsLayer {
                                    if (isDragging) {
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    } else {
                                        scaleX = 1f
                                        scaleY = 1f
                                    }
                                },
                        )
                    }
                }
            }
        }
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            AddModelButton(
                models = modelList,
                selectedModels = providerSetting.models,
                onAddModel = {
                    onUpdateProvider(providerSetting.addModel(it))
                },
                onRemoveModel = {
                    onUpdateProvider(providerSetting.delModel(it))
                },
                expanded = expanded,
                parentProvider = providerSetting,
                onUpdateProvider = onUpdateProvider
            )
        }
    }
}

/** Short headline for a connection verdict (English; localization deferred per CLAUDE.md i18n rule). */
internal fun connectionResultTitle(result: ConnectionResult): String = when (result) {
    is ConnectionResult.Valid ->
        if (result.rateLimited) "Connected (rate-limited)" else "Connected"

    is ConnectionResult.InvalidKey -> "Invalid API key"
    ConnectionResult.ReachableNoModelList -> "Connected — no model list"
    ConnectionResult.UnreachableOrWrongEndpoint -> "Couldn't reach the provider"
}

/** Actionable next-step hint for a connection verdict. */
internal fun connectionResultHint(result: ConnectionResult): String = when (result) {
    is ConnectionResult.Valid ->
        if (result.rateLimited) "Rate limited — wait a moment and test again."
        else "Found ${result.modelCount} models."

    is ConnectionResult.InvalidKey -> "Check your API key, then test the connection again."
    ConnectionResult.ReachableNoModelList ->
        "This provider doesn't expose a model list. Add models manually."

    ConnectionResult.UnreachableOrWrongEndpoint ->
        "Check the Base URL / API Path, or add a model manually and test again."
}
