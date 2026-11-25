package me.rerere.rikkahub.ui.pages.pocketbase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.pocketbase.PbCollection
import me.rerere.rikkahub.data.pocketbase.PbRecordsPage
import me.rerere.rikkahub.data.pocketbase.PocketBaseClient
import me.rerere.rikkahub.data.pocketbase.PocketBaseSettingsStore

enum class CollectionDetailTab(val label: String) {
    RECORDS("记录"),
    SCHEMA("字段结构"),
    RULES("规则与索引"),
}

data class PocketBaseCollectionUiState(
    val collectionName: String,
    val collection: PbCollection? = null,
    val recordsPage: PbRecordsPage? = null,
    val selectedTab: CollectionDetailTab = CollectionDetailTab.RECORDS,
    val sort: String = "-created",
    val filterQuery: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val realtimeActive: Boolean = false,
    val lastRealtimeAction: String? = null,
)

class PocketBaseCollectionVM(
    private val collectionName: String,
    private val client: PocketBaseClient,
    private val settingsStore: PocketBaseSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PocketBaseCollectionUiState(
            collectionName = collectionName,
            realtimeActive = settingsStore.realtimeEnabled.value,
        ),
    )
    val uiState: StateFlow<PocketBaseCollectionUiState> = _uiState.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        loadInitial()
        if (settingsStore.realtimeEnabled.value) {
            startRealtime()
        }
    }

    fun selectTab(tab: CollectionDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSort(sort: String) {
        _uiState.update { it.copy(sort = sort) }
        loadRecords(page = 1)
    }

    fun setFilterQuery(filter: String) {
        _uiState.update { it.copy(filterQuery = filter) }
    }

    fun applyFilter() {
        loadRecords(page = 1)
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionMessage = null) }
    }

    fun loadInitial() {
        val session = settingsStore.activeSession.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                coroutineScope {
                    val colDef = async { client.getCollection(session, collectionName) }
                    val recDef = async {
                        client.listRecords(
                            session = session,
                            collectionName = collectionName,
                            page = 1,
                            perPage = 30,
                            sort = _uiState.value.sort.takeIf { it.isNotBlank() },
                            filter = _uiState.value.filterQuery.trim().takeIf { it.isNotEmpty() },
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            collection = colDef.await(),
                            recordsPage = recDef.await(),
                        )
                    }
                }
            }.onFailure { err ->
                // View collections might not support -created sort if no created field exists; retry without sort
                if (_uiState.value.sort == "-created") {
                    _uiState.update { it.copy(sort = "") }
                    loadRecords(page = 1)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "加载失败") }
                }
            }
        }
    }

    fun loadRecords(page: Int = _uiState.value.recordsPage?.page ?: 1) {
        val session = settingsStore.activeSession.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                client.listRecords(
                    session = session,
                    collectionName = collectionName,
                    page = page,
                    perPage = 30,
                    sort = _uiState.value.sort.takeIf { it.isNotBlank() },
                    filter = _uiState.value.filterQuery.trim().takeIf { it.isNotEmpty() },
                )
            }.onSuccess { result ->
                _uiState.update { it.copy(isLoading = false, recordsPage = result) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "查询记录失败") }
            }
        }
    }

    fun createRecord(data: JsonObject, onSuccess: () -> Unit) {
        val session = settingsStore.activeSession.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                client.createRecord(session, collectionName, data)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, actionMessage = "记录已创建") }
                loadRecords(page = 1)
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "创建失败") }
            }
        }
    }

    fun updateRecord(recordId: String, data: JsonObject, onSuccess: () -> Unit) {
        val session = settingsStore.activeSession.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                client.updateRecord(session, collectionName, recordId, data)
            }.onSuccess { updated ->
                _uiState.update { state ->
                    val page = state.recordsPage
                    val newItems = page?.items?.map { item ->
                        if (item["id"]?.jsonPrimitive?.contentOrNull == recordId) updated else item
                    }
                    state.copy(
                        isSaving = false,
                        actionMessage = "记录已更新",
                        recordsPage = if (page != null && newItems != null) page.copy(items = newItems) else page,
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "更新失败") }
            }
        }
    }

    fun deleteRecord(recordId: String, onSuccess: () -> Unit) {
        val session = settingsStore.activeSession.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                client.deleteRecord(session, collectionName, recordId)
            }.onSuccess {
                _uiState.update { state ->
                    val page = state.recordsPage
                    val newItems = page?.items?.filterNot {
                        it["id"]?.jsonPrimitive?.contentOrNull == recordId
                    }
                    state.copy(
                        isSaving = false,
                        actionMessage = "记录已删除",
                        recordsPage = if (page != null && newItems != null) {
                            page.copy(items = newItems, totalItems = (page.totalItems - 1).coerceAtLeast(0))
                        } else {
                            page
                        },
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "删除失败") }
            }
        }
    }

    fun toggleRealtime() {
        val next = !_uiState.value.realtimeActive
        _uiState.update { it.copy(realtimeActive = next) }
        if (next) {
            startRealtime()
        } else {
            realtimeJob?.cancel()
            realtimeJob = null
        }
    }

    private fun startRealtime() {
        realtimeJob?.cancel()
        val session = settingsStore.activeSession.value ?: return
        realtimeJob = viewModelScope.launch {
            client.observeCollection(session, collectionName).collect { event ->
                val recordId = event.record["id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                _uiState.update { state ->
                    val page = state.recordsPage ?: return@update state
                    val updatedItems = when (event.action) {
                        "create" -> {
                            if (page.page == 1) {
                                listOf(event.record) + page.items.filterNot {
                                    it["id"]?.jsonPrimitive?.contentOrNull == recordId
                                }
                            } else {
                                page.items
                            }
                        }
                        "update" -> page.items.map { item ->
                            if (item["id"]?.jsonPrimitive?.contentOrNull == recordId) event.record else item
                        }
                        "delete" -> page.items.filterNot {
                            it["id"]?.jsonPrimitive?.contentOrNull == recordId
                        }
                        else -> page.items
                    }
                    val totalDelta = when (event.action) {
                        "create" -> 1
                        "delete" -> -1
                        else -> 0
                    }
                    state.copy(
                        recordsPage = page.copy(
                            items = updatedItems,
                            totalItems = (page.totalItems + totalDelta).coerceAtLeast(0),
                        ),
                        lastRealtimeAction = "${event.action} · $recordId",
                    )
                }
            }
        }
    }
}
