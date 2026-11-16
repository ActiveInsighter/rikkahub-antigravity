package me.rerere.rikkahub.ui.pages.workflow

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.workflow.FileConversionConfig
import me.rerere.rikkahub.data.workflow.LibraryFile
import me.rerere.rikkahub.data.workflow.PdfConversionOptions
import me.rerere.rikkahub.data.workflow.PdfJob
import me.rerere.rikkahub.data.workflow.WorkflowConverterRepository
import me.rerere.rikkahub.data.workflow.WorkflowLibraryRepository
import me.rerere.rikkahub.data.workflow.WorkflowRepository

data class WorkflowConverterUiState(
    val config: FileConversionConfig? = null,
    val isLoadingConfig: Boolean = false,
    val isSavingConfig: Boolean = false,
    val configError: String? = null,
    val jobs: List<PdfJob> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val isLoadingJobs: Boolean = false,
    val isRefreshingJobs: Boolean = false,
    val isCreatingJob: Boolean = false,
    val isMutatingJob: Boolean = false,
    val isUploadingPdf: Boolean = false,
    val uploadingFileName: String? = null,
    val uploadProgress: Int? = null,
    val uploadedLibraryFile: LibraryFile? = null,
    val preselectedLibraryFile: LibraryFile? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
)

class WorkflowConverterVM(
    private val workflowRepository: WorkflowRepository,
    private val converterRepository: WorkflowConverterRepository,
    private val libraryRepository: WorkflowLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkflowConverterUiState())
    val uiState: StateFlow<WorkflowConverterUiState> = _uiState.asStateFlow()

    private var jobsJob: Job? = null
    private var pollJob: Job? = null
    private var pendingJobId: String = converterRepository.newRecordId()

    init {
        viewModelScope.launch {
            workflowRepository.session.collectLatest { session ->
                if (session != null) {
                    loadConfig()
                    loadJobs(page = 1, isRefresh = false)
                } else {
                    jobsJob?.cancel()
                    pollJob?.cancel()
                    _uiState.value = WorkflowConverterUiState()
                }
            }
        }
    }

    fun refresh() {
        loadConfig()
        loadJobs(page = _uiState.value.page, isRefresh = true)
    }

    fun setPage(page: Int) {
        val target = page.coerceIn(1, _uiState.value.totalPages.coerceAtLeast(1))
        if (target == _uiState.value.page) return
        loadJobs(page = target, isRefresh = false)
    }

    fun preselectLibraryFile(file: LibraryFile?) {
        _uiState.update { it.copy(preselectedLibraryFile = file) }
    }

    fun clearUploadedPdf() {
        _uiState.update { it.copy(uploadedLibraryFile = null, uploadProgress = null, uploadingFileName = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, configError = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun saveConfig(
        options: PdfConversionOptions,
        systemPrompt: String,
        onSuccess: () -> Unit = {},
    ) {
        val current = _uiState.value.config ?: return
        if (_uiState.value.isSavingConfig) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingConfig = true, configError = null) }
            converterRepository.savePdfConversionConfig(current, options, systemPrompt)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            config = saved,
                            isSavingConfig = false,
                            noticeMessage = "已保存转换配置（v${saved.revision}）",
                        )
                    }
                    onSuccess()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSavingConfig = false,
                            configError = err.message ?: "保存转换配置失败",
                        )
                    }
                }
        }
    }

    fun uploadPdfFromUri(context: Context, uri: Uri) {
        if (_uiState.value.isUploadingPdf) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingPdf = true,
                    uploadingFileName = null,
                    uploadProgress = 0,
                    errorMessage = null,
                )
            }
            val meta = withContext(Dispatchers.IO) { resolvePdfMeta(appContext, uri) }
            if (meta == null) {
                _uiState.update {
                    it.copy(
                        isUploadingPdf = false,
                        uploadProgress = null,
                        errorMessage = "无法读取所选 PDF 文件",
                    )
                }
                return@launch
            }
            val (fileName, fileSize) = meta
            _uiState.update { it.copy(uploadingFileName = fileName) }

            libraryRepository.uploadLibraryPdf(
                fileName = fileName,
                fileSize = fileSize,
                openStream = { appContext.contentResolver.openInputStream(uri) },
                onProgress = { pct -> _uiState.update { state -> state.copy(uploadProgress = pct) } },
            ).onSuccess { uploaded ->
                _uiState.update {
                    it.copy(
                        isUploadingPdf = false,
                        uploadingFileName = null,
                        uploadProgress = null,
                        uploadedLibraryFile = uploaded,
                        noticeMessage = "已上传「${uploaded.fileName}」",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUploadingPdf = false,
                        uploadingFileName = null,
                        uploadProgress = null,
                        errorMessage = err.message ?: "上传 PDF 失败",
                    )
                }
            }
        }
    }

    fun createJob(
        title: String,
        sourceUrl: String,
        outputName: String,
        prompt: String,
        startPage: Int?,
        endPage: Int?,
        adjacentPages: Boolean,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isCreatingJob) return
        val configId = _uiState.value.config?.id.orEmpty()
        if (configId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "转换配置尚未加载，请稍后重试") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingJob = true, errorMessage = null) }
            converterRepository.createPdfJob(
                id = pendingJobId,
                title = title,
                sourceUrl = sourceUrl,
                outputName = outputName,
                prompt = prompt,
                configId = configId,
                startPage = startPage,
                endPage = endPage,
                adjacentPages = adjacentPages,
            ).onSuccess { created ->
                pendingJobId = converterRepository.newRecordId()
                _uiState.update {
                    it.copy(
                        isCreatingJob = false,
                        uploadedLibraryFile = null,
                        preselectedLibraryFile = null,
                        noticeMessage = "已提交转换任务「${created.displayTitle}」",
                    )
                }
                loadJobs(page = 1, isRefresh = true)
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreatingJob = false,
                        errorMessage = err.message ?: "提交转换任务失败",
                    )
                }
            }
        }
    }

    fun cancelJob(job: PdfJob) {
        if (_uiState.value.isMutatingJob) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutatingJob = true, errorMessage = null) }
            converterRepository.cancelPdfJob(job)
                .onSuccess {
                    _uiState.update { it.copy(isMutatingJob = false, noticeMessage = "已请求取消任务") }
                    loadJobs(page = _uiState.value.page, isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutatingJob = false, errorMessage = err.message ?: "取消任务失败")
                    }
                }
        }
    }

    fun rerunJob(job: PdfJob) {
        if (_uiState.value.isMutatingJob) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutatingJob = true, errorMessage = null) }
            converterRepository.rerunPdfJob(converterRepository.newRecordId(), job)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(isMutatingJob = false, noticeMessage = "已重新提交「${created.displayTitle}」")
                    }
                    loadJobs(page = 1, isRefresh = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutatingJob = false, errorMessage = err.message ?: "重跑任务失败")
                    }
                }
        }
    }

    fun requestDownloadUrl(job: PdfJob, onUrlReady: (String) -> Unit) {
        if (_uiState.value.isMutatingJob) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutatingJob = true, errorMessage = null) }
            converterRepository.getPdfDownloadUrl(job)
                .onSuccess { url ->
                    _uiState.update { it.copy(isMutatingJob = false) }
                    onUrlReady(url)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isMutatingJob = false, errorMessage = err.message ?: "获取下载链接失败")
                    }
                }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConfig = it.config == null, configError = null) }
            converterRepository.getPdfConversionConfig()
                .onSuccess { cfg ->
                    _uiState.update { it.copy(config = cfg, isLoadingConfig = false, configError = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingConfig = false,
                            configError = err.message ?: "加载转换配置失败",
                        )
                    }
                }
        }
    }

    private fun loadJobs(page: Int, isRefresh: Boolean) {
        jobsJob?.cancel()
        jobsJob = viewModelScope.launch {
            val hasData = _uiState.value.jobs.isNotEmpty()
            _uiState.update {
                it.copy(
                    page = page,
                    isLoadingJobs = !isRefresh && !hasData,
                    isRefreshingJobs = isRefresh || hasData,
                    errorMessage = null,
                )
            }
            converterRepository.listPdfJobs(page)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            jobs = result.items,
                            page = result.page,
                            totalPages = result.totalPages,
                            totalItems = result.totalItems,
                            isLoadingJobs = false,
                            isRefreshingJobs = false,
                        )
                    }
                    schedulePollIfNeeded(result.items)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingJobs = false,
                            isRefreshingJobs = false,
                            errorMessage = err.message ?: "加载转换记录失败",
                        )
                    }
                }
        }
    }

    private fun schedulePollIfNeeded(jobs: List<PdfJob>) {
        pollJob?.cancel()
        if (jobs.none { it.status.isActive }) return
        pollJob = viewModelScope.launch {
            delay(5_000L)
            loadJobs(page = _uiState.value.page, isRefresh = true)
        }
    }

    private fun resolvePdfMeta(context: Context, uri: Uri): Pair<String, Long>? {
        val resolver = context.contentResolver
        var displayName: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
        }
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.trim()?.takeIf { it.isNotEmpty() }
        val rawName = displayName ?: fallbackName ?: "document.pdf"
        val finalName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
        val finalSize = if (size > 0L) size else runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { len -> len > 0L } }
                ?: resolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                    }
                    total
                } ?: -1L
        }.getOrDefault(-1L)
        if (finalSize <= 0L) return null
        return finalName to finalSize
    }
}
