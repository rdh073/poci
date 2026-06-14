package me.rerere.rikkahub.ui.components.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceCwdPolicy
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceStorageArea

class WorkspaceSheetVM(
    store: WorkspaceSheetStore,
) : ViewModel() {
    private val controller = WorkspaceSheetController(store, viewModelScope)
    val state = controller.state

    fun activate(
        assistantWorkspaceId: String?,
        onAutoSelectWorkspace: (String) -> Unit = {},
    ) = controller.activate(assistantWorkspaceId, onAutoSelectWorkspace)

    fun syncAssistantWorkspaceId(assistantWorkspaceId: String?) {
        controller.syncAssistantWorkspaceId(assistantWorkspaceId)
    }

    fun selectWorkspace(
        id: String,
        onAssistantWorkspaceSelected: (String) -> Unit,
    ) = controller.selectWorkspace(id, onAssistantWorkspaceSelected)

    fun open(entry: WorkspaceFileEntry) = controller.open(entry)

    fun goUp() = controller.goUp()

    fun setCurrentAsProjectDir() = controller.setCurrentAsProjectDir()

    override fun onCleared() {
        controller.close()
    }
}

class WorkspaceSheetController(
    private val store: WorkspaceSheetStore,
    scope: CoroutineScope,
) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + job)
    private val _state = MutableStateFlow(WorkspaceSheetState())
    val state = _state.asStateFlow()

    private var activated = false
    private var listJob: Job? = null
    private var browseJob: Job? = null
    private var lastAssistantWorkspaceId: String? = null
    private var autoSelected = false

    fun activate(
        assistantWorkspaceId: String?,
        onAutoSelectWorkspace: (String) -> Unit = {},
    ) {
        lastAssistantWorkspaceId = assistantWorkspaceId
        if (activated) return
        activated = true
        _state.update { it.copy(activated = true) }
        listJob = scope.launch {
            store.workspacesFlow().collect { rows ->
                foldRows(rows, onAutoSelectWorkspace)
            }
        }
    }

    fun syncAssistantWorkspaceId(assistantWorkspaceId: String?) {
        lastAssistantWorkspaceId = assistantWorkspaceId
        if (!activated || assistantWorkspaceId == null) return
        if (_state.value.selectedWorkspaceId != assistantWorkspaceId &&
            _state.value.workspaces.any { it.id == assistantWorkspaceId }
        ) {
            selectWorkspace(assistantWorkspaceId) {}
        }
    }

    fun selectWorkspace(
        id: String,
        onAssistantWorkspaceSelected: (String) -> Unit,
    ) {
        if (_state.value.workspaces.none { it.id == id }) return
        if (_state.value.selectedWorkspaceId == id) return
        selectLocal(id)
        onAssistantWorkspaceSelected(id)
        loadStartPath(id)
    }

    fun open(entry: WorkspaceFileEntry) {
        if (entry.isDirectory) browseTo(entry.path)
    }

    fun browseTo(path: String) {
        val id = _state.value.selectedWorkspaceId ?: return
        val normalized = WorkspaceCwdPolicy.normalize(path)
        _state.update {
            it.copy(path = normalized, entries = emptyList(), loading = true, error = null)
        }
        loadEntries(id, normalized)
    }

    fun goUp() {
        val current = _state.value.path
        if (current.isBlank()) return
        browseTo(current.substringBeforeLast('/', missingDelimiterValue = ""))
    }

    fun setCurrentAsProjectDir() {
        val id = _state.value.selectedWorkspaceId ?: return
        val current = WorkspaceCwdPolicy.normalize(_state.value.path)
        val project = WorkspaceCwdPolicy.normalize(_state.value.projectDir)
        if (current == project) return
        scope.launch {
            _state.update { it.copy(settingProjectDir = true, error = null) }
            try {
                if (store.setWorkingDir(id, current)) {
                    _state.update { state ->
                        if (state.selectedWorkspaceId == id) {
                            state.copy(projectDir = current, settingProjectDir = false)
                        } else {
                            state.copy(settingProjectDir = false)
                        }
                    }
                } else {
                    _state.update { it.copy(settingProjectDir = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(settingProjectDir = false, error = e.message) }
            }
        }
    }

    fun close() {
        browseJob?.cancel()
        listJob?.cancel()
        scope.cancel()
    }

    private fun foldRows(
        rows: List<WorkspaceSheetWorkspace>,
        onAutoSelectWorkspace: (String) -> Unit,
    ) {
        val previous = _state.value
        val currentSelection = previous.selectedWorkspaceId?.takeIf { id -> rows.any { it.id == id } }
        val assistantSelection = lastAssistantWorkspaceId?.takeIf { id -> rows.any { it.id == id } }
        val autoSelection = if (!autoSelected && lastAssistantWorkspaceId == null && rows.size == 1) {
            rows.single().id
        } else {
            null
        }
        val selected = currentSelection ?: assistantSelection ?: autoSelection
        if (autoSelection != null) {
            autoSelected = true
            onAutoSelectWorkspace(autoSelection)
        }

        _state.update {
            it.copy(
                workspaces = rows,
                selectedWorkspaceId = selected,
                entries = if (selected == null) emptyList() else it.entries,
            )
        }

        val previousRow = previous.workspaces.firstOrNull { it.id == selected }
        val currentRow = rows.firstOrNull { it.id == selected }
        if (selected == null) {
            browseJob?.cancel()
            _state.update {
                it.copy(projectDir = "", path = "", entries = emptyList(), loading = false, error = null)
            }
        } else if (selected != previous.selectedWorkspaceId) {
            selectLocal(selected)
            loadStartPath(selected)
        } else if (currentRow?.workingDir != previousRow?.workingDir) {
            _state.update { it.copy(loading = true, error = null) }
            loadStartPath(selected)
        }
    }

    private fun selectLocal(id: String) {
        _state.update {
            it.copy(
                selectedWorkspaceId = id,
                projectDir = "",
                path = "",
                entries = emptyList(),
                loading = true,
                error = null,
            )
        }
    }

    private fun loadStartPath(id: String) {
        browseJob?.cancel()
        browseJob = scope.launch {
            try {
                val start = store.resolvedProjectDir(id)
                if (_state.value.selectedWorkspaceId != id) return@launch
                _state.update {
                    it.copy(projectDir = start, path = start, entries = emptyList(), loading = true, error = null)
                }
                val entries = store.listFiles(id, start)
                _state.update { state ->
                    if (state.selectedWorkspaceId == id && state.path == start) {
                        state.copy(entries = entries, loading = false)
                    } else {
                        state
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value.selectedWorkspaceId == id) {
                    _state.update { it.copy(entries = emptyList(), loading = false, error = e.message) }
                }
            }
        }
    }

    private fun loadEntries(id: String, path: String) {
        browseJob?.cancel()
        browseJob = scope.launch {
            try {
                val entries = store.listFiles(id, path)
                _state.update { state ->
                    if (state.selectedWorkspaceId == id && state.path == path) {
                        state.copy(entries = entries, loading = false)
                    } else {
                        state
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_state.value.selectedWorkspaceId == id && _state.value.path == path) {
                    _state.update { it.copy(entries = emptyList(), loading = false, error = e.message) }
                }
            }
        }
    }
}

interface WorkspaceSheetStore {
    fun workspacesFlow(): Flow<List<WorkspaceSheetWorkspace>>

    suspend fun resolvedProjectDir(id: String): String

    suspend fun listFiles(id: String, path: String): List<WorkspaceFileEntry>

    suspend fun setWorkingDir(id: String, path: String): Boolean
}

class DefaultWorkspaceSheetStore(
    private val repository: WorkspaceRepository,
) : WorkspaceSheetStore {
    override fun workspacesFlow(): Flow<List<WorkspaceSheetWorkspace>> =
        repository.listFlow().map { rows ->
            rows.map {
                WorkspaceSheetWorkspace(
                    id = it.id,
                    name = it.name,
                    workingDir = it.workingDir,
                )
            }
        }

    override suspend fun resolvedProjectDir(id: String): String = repository.resolvedWorkingDir(id)

    override suspend fun listFiles(id: String, path: String): List<WorkspaceFileEntry> =
        repository.listFiles(id = id, area = WorkspaceStorageArea.FILES, path = path)

    override suspend fun setWorkingDir(id: String, path: String): Boolean =
        repository.setWorkingDir(id, path)
}

data class WorkspaceSheetState(
    val activated: Boolean = false,
    val workspaces: List<WorkspaceSheetWorkspace> = emptyList(),
    val selectedWorkspaceId: String? = null,
    val projectDir: String = "",
    val path: String = "",
    val entries: List<WorkspaceFileEntry> = emptyList(),
    val loading: Boolean = false,
    val settingProjectDir: Boolean = false,
    val error: String? = null,
)

data class WorkspaceSheetWorkspace(
    val id: String,
    val name: String,
    val workingDir: String,
)
