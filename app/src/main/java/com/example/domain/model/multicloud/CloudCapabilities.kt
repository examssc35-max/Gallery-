package com.example.domain.model.multicloud

enum class CloudOperation {
    BROWSE,
    SEARCH,
    UPLOAD,
    DOWNLOAD,
    DELETE,
    RENAME,
    MOVE,
    CREATE_FOLDER,
    STORAGE_USAGE,
    BACKGROUND_SYNC
}

data class CloudCapabilities(
    val canBrowse: Boolean = true,
    val canSearch: Boolean = true,
    val canUpload: Boolean = true,
    val canDownload: Boolean = true,
    val canDelete: Boolean = true,
    val canRename: Boolean = false,
    val canMove: Boolean = false,
    val canCreateFolder: Boolean = false,
    val supportsThumbnails: Boolean = true,
    val supportsStorageUsage: Boolean = true,
    val supportsBackgroundSync: Boolean = true
) {
    fun isSupported(operation: CloudOperation): Boolean = when (operation) {
        CloudOperation.BROWSE -> canBrowse
        CloudOperation.SEARCH -> canSearch
        CloudOperation.UPLOAD -> canUpload
        CloudOperation.DOWNLOAD -> canDownload
        CloudOperation.DELETE -> canDelete
        CloudOperation.RENAME -> canRename
        CloudOperation.MOVE -> canMove
        CloudOperation.CREATE_FOLDER -> canCreateFolder
        CloudOperation.STORAGE_USAGE -> supportsStorageUsage
        CloudOperation.BACKGROUND_SYNC -> supportsBackgroundSync
    }

    fun getUnsupportedReason(operation: CloudOperation, providerName: String): String = when (operation) {
        CloudOperation.DELETE -> "$providerName currently does not allow file deletion through the official API."
        CloudOperation.RENAME -> "$providerName does not support remote file renaming via this integration."
        CloudOperation.MOVE -> "$providerName does not support moving files via this integration."
        CloudOperation.CREATE_FOLDER -> "$providerName does not support arbitrary folder creation."
        CloudOperation.STORAGE_USAGE -> "Storage usage is unavailable via the $providerName API."
        else -> "This operation is not supported by $providerName."
    }
}
