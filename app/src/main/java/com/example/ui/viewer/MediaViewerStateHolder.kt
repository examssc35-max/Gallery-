package com.example.ui.viewer

import com.example.domain.model.MediaItem

/**
 * Singleton state holder for the media viewer to ensure seamless handoff
 * across screens (Gallery, Smart Collections, AI Assistant) without losing
 * active media lists during navigation transitions or recompositions.
 */
object MediaViewerStateHolder {
    @Volatile
    var activeViewerList: List<MediaItem> = emptyList()
        private set

    @Volatile
    var activeViewerIndex: Int = 0
        private set

    @Volatile
    var activeViewerAutoPlayVideo: Boolean = false
        private set

    fun setViewerData(
        items: List<MediaItem>,
        index: Int = 0,
        autoPlayVideo: Boolean = false
    ) {
        activeViewerList = items
        activeViewerIndex = if (items.isNotEmpty()) index.coerceIn(0, items.size - 1) else 0
        activeViewerAutoPlayVideo = autoPlayVideo
    }

    fun clear() {
        activeViewerList = emptyList()
        activeViewerIndex = 0
        activeViewerAutoPlayVideo = false
    }
}
