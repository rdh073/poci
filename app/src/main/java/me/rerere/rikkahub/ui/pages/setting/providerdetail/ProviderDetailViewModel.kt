package me.rerere.rikkahub.ui.pages.setting.providerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ConnectionResult
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProbeOutcome
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.classifyProviderConnection

/**
 * Whether the most recent connection test has run, is running, or produced a verdict. The UI
 * branches only on [ConnectionResult] (via [Done]) — never on a raw HTTP status — so "wrong key vs
 * no model list vs wrong endpoint" lives in one place ([classifyProviderConnection]).
 */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data class Done(val result: ConnectionResult) : ConnectionState
}

/**
 * The fetched model catalog used by the model browser. Replaces the old `produceState(emptyList())`
 * where a failed fetch and an empty success were observationally identical: [Failed] now carries the
 * classified reason so the UI can tell the user WHY no models appeared (bad key / no /models / wrong
 * endpoint) and offer the right next step.
 */
sealed interface ModelCatalogState {
    data object Idle : ModelCatalogState
    data object Loading : ModelCatalogState
    data class Loaded(val models: List<Model>) : ModelCatalogState
    data class Failed(val result: ConnectionResult) : ModelCatalogState
}

/**
 * Owns the connection-test and model-catalog state for one provider-detail screen. The provider
 * setting is passed per-action (not held) so the screen's live config draft always drives the probe.
 */
class ProviderDetailViewModel(
    private val providerManager: ProviderManager,
) : ViewModel() {

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection = _connection.asStateFlow()

    private val _catalog = MutableStateFlow<ModelCatalogState>(ModelCatalogState.Idle)
    val catalog = _catalog.asStateFlow()

    private var connectionJob: Job? = null
    private var catalogJob: Job? = null

    /** Fetch the model catalog for the browser, surfacing failure as a classified [ModelCatalogState.Failed]. */
    fun refreshCatalog(setting: ProviderSetting) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _catalog.value = ModelCatalogState.Loading
            val (result, models) = probe(setting)
            _catalog.value =
                if (models.isNotEmpty()) ModelCatalogState.Loaded(models)
                else ModelCatalogState.Failed(result)
            // Keep the banner in sync — the same probe already produced a verdict.
            _connection.value = ConnectionState.Done(result)
        }
    }

    /** Run an explicit connection test, updating the banner (and the catalog if models came back). */
    fun testConnection(setting: ProviderSetting) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            _connection.value = ConnectionState.Testing
            val (result, models) = probe(setting)
            _connection.value = ConnectionState.Done(result)
            if (models.isNotEmpty()) _catalog.value = ModelCatalogState.Loaded(models)
        }
    }

    fun resetConnection() {
        connectionJob?.cancel()
        _connection.value = ConnectionState.Idle
    }

    /**
     * One probe round: fetch the model list, then spend a chat probe ONLY when the list didn't
     * already prove the connection (to disambiguate a /models failure) AND a real model id exists to
     * probe with — never invent one (maintainer decision). Returns the verdict plus the fetched
     * catalog (empty unless the list call succeeded).
     */
    private suspend fun probe(setting: ProviderSetting): Pair<ConnectionResult, List<Model>> {
        val provider = providerManager.getProviderByType(setting)
        val modelsProbe = provider.probeModelList(setting)

        val listProvedConnection = (modelsProbe.outcome as? ProbeOutcome.Http)
            ?.body
            ?.let { it is ProbeOutcome.Body.ModelList && it.count > 0 }
            ?: false

        val chatProbe = if (!listProvedConnection) {
            val modelId = setting.models.firstOrNull { it.type == ModelType.CHAT }?.modelId
                ?: setting.models.firstOrNull()?.modelId
            modelId?.let { provider.probeChat(setting, it) }
        } else {
            null
        }

        val result = classifyProviderConnection(modelsProbe.outcome, chatProbe)
        return result to modelsProbe.models
    }
}
