package com.example.domain.model.multicloud

enum class CloudProviderType(
    val id: String,
    val displayName: String,
    val iconDescription: String
) {
    R2("r2", "Cloudflare R2", "Cloudflare R2 Storage"),
    GOOGLE_PHOTOS("google_photos", "Google Photos", "Google Photos"),
    ONEDRIVE("onedrive", "Microsoft OneDrive", "Microsoft OneDrive"),
    DROPBOX("dropbox", "Dropbox", "Dropbox Storage");

    companion object {
        fun fromId(id: String?): CloudProviderType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: R2
    }
}
