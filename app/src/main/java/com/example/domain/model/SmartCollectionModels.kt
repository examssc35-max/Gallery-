package com.example.domain.model

data class SmartCollectionCategory(
    val id: String,
    val displayName: String,
    val description: String,
    val iconName: String,
    val defaultOrder: Int
)

data class SmartCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val itemCount: Int = 0,
    val coverMediaItem: MediaItem? = null,
    val coverUri: String? = null
)

data class CategoryConfidence(
    val categoryId: String,
    val categoryDisplayName: String,
    val confidence: Float
)

data class MediaClassificationResult(
    val mediaId: Long,
    val categories: List<CategoryConfidence>,
    val isAnalyzed: Boolean,
    val errorMessage: String? = null
)

enum class AnalysisRunState {
    IDLE,
    ANALYZING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class AnalysisStatus(
    val state: AnalysisRunState = AnalysisRunState.IDLE,
    val totalMediaCount: Int = 0,
    val analyzedCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val currentItemName: String? = null,
    val progress: Float = 0f,
    val message: String = "Up to date"
)

data class SmartCollectionSettings(
    val isEnabled: Boolean = true,
    val autoAnalyzeNewMedia: Boolean = true,
    val analyzeVideos: Boolean = true,
    val wifiOnly: Boolean = false,
    val requireCharging: Boolean = false,
    val modelVersion: String = "v1.0",
    val isModelInstalled: Boolean = false
)

object SmartCollectionCategories {
    val ALL = listOf(
        SmartCollectionCategory("people", "People", "Photos with people and portraits", "person", 1),
        SmartCollectionCategory("family_moments", "Family Moments", "Family-style gatherings and moments", "family_restroom", 2),
        SmartCollectionCategory("friends_group", "Friends & Group Photos", "Group photos and celebrations", "groups", 3),
        SmartCollectionCategory("nature", "Nature", "Flowers, trees, greenery and outdoors", "park", 4),
        SmartCollectionCategory("food", "Food", "Dishes, meals, drinks, and dining", "restaurant", 5),
        SmartCollectionCategory("travel", "Travel", "Vacations, scenery, and sightseeing", "flight", 6),
        SmartCollectionCategory("animals_pets", "Animals / Pets", "Pets, domestic animals, and wildlife", "pets", 7),
        SmartCollectionCategory("documents", "Documents", "Receipts, notes, whiteboards, and forms", "description", 8),
        SmartCollectionCategory("screenshots", "Screenshots", "App screens, receipts, and capture cards", "smartphone", 9),
        SmartCollectionCategory("sports", "Sports", "Athletics, fitness, bikes, and outdoor games", "fitness_center", 10),
        SmartCollectionCategory("vehicles", "Vehicles", "Cars, bicycles, planes, and transport", "directions_car", 11),
        SmartCollectionCategory("buildings", "Buildings", "Architecture, houses, and cityscapes", "location_city", 12),
        SmartCollectionCategory("events", "Events", "Parties, concerts, and celebrations", "celebration", 13),
        SmartCollectionCategory("technology", "Technology", "Computers, electronics, gadgets, and gear", "devices", 14),
        SmartCollectionCategory("products", "Products", "Shopping items, merchandise, and packages", "shopping_bag", 15),
        SmartCollectionCategory("landscapes", "Landscapes", "Sunsets, horizons, beaches, and mountains", "landscape", 16),
        SmartCollectionCategory("other", "Other", "Miscellaneous categorized media", "category", 17)
    )

    fun findById(id: String): SmartCollectionCategory? {
        val normalized = id.lowercase().trim()
        return ALL.firstOrNull { it.id == normalized }
    }
}
