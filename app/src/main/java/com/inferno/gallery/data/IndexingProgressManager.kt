package com.inferno.gallery.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IndexingProgressManager {
    data class ProgressState(
        val isIndexing: Boolean = false,
        val progress: Int = 0,
        val total: Int = 0,
        val currentImageName: String? = null,
        val recentUris: List<String> = emptyList(),
        val facesFound: Int = 0,
        val failed: Int = 0,
        val stage: String = ""
    )

    private val _ocrProgress = MutableStateFlow(ProgressState())
    val ocrProgress: StateFlow<ProgressState> = _ocrProgress.asStateFlow()

    private val _clipProgress = MutableStateFlow(ProgressState())
    val clipProgress: StateFlow<ProgressState> = _clipProgress.asStateFlow()

    private val _faceProgress = MutableStateFlow(ProgressState())
    val faceProgress: StateFlow<ProgressState> = _faceProgress.asStateFlow()

    fun updateOcrProgress(
        isIndexing: Boolean,
        progress: Int,
        total: Int,
        currentImageName: String? = null,
        recentUris: List<String> = emptyList(),
        failed: Int = 0,
        stage: String = ""
    ) {
        _ocrProgress.value = ProgressState(
            isIndexing = isIndexing,
            progress = progress,
            total = total,
            currentImageName = currentImageName,
            recentUris = recentUris,
            failed = failed,
            stage = stage
        )
    }

    fun updateClipProgress(
        isIndexing: Boolean,
        progress: Int,
        total: Int,
        currentImageName: String? = null,
        recentUris: List<String> = emptyList(),
        failed: Int = 0,
        stage: String = ""
    ) {
        _clipProgress.value = ProgressState(
            isIndexing = isIndexing,
            progress = progress,
            total = total,
            currentImageName = currentImageName,
            recentUris = recentUris,
            failed = failed,
            stage = stage
        )
    }

    fun updateFaceProgress(
        isIndexing: Boolean,
        progress: Int,
        total: Int,
        currentImageName: String? = null,
        recentUris: List<String> = emptyList(),
        facesFound: Int = 0,
        failed: Int = 0,
        stage: String = ""
    ) {
        _faceProgress.value = ProgressState(
            isIndexing = isIndexing,
            progress = progress,
            total = total,
            currentImageName = currentImageName,
            recentUris = recentUris,
            facesFound = facesFound,
            failed = failed,
            stage = stage
        )
    }
}
