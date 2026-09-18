package com.example.navigation

sealed class NavRoute(val route: String) {
    data object Gallery : NavRoute("gallery")
    data object MediaViewer : NavRoute("viewer/{index}") {
        fun createRoute(index: Int) = "viewer/$index"
    }
    data object Backup : NavRoute("backup")
    data object Cloud : NavRoute("cloud")
    data object Settings : NavRoute("settings")
    data object ThemeSettings : NavRoute("theme_settings")
    data object Duplicates : NavRoute("duplicates")
    data object Storage : NavRoute("storage")
    data object CloudStorageUsage : NavRoute("cloud_storage_usage")
    data object Trash : NavRoute("trash")
    data object AiAssistant : NavRoute("ai_assistant")
    data object SmartCollections : NavRoute("smart_collections")
    data object SmartCollectionDetail : NavRoute("smart_collection/{collectionId}") {
        fun createRoute(collectionId: String) = "smart_collection/$collectionId"
    }
    data object SmartCollectionsSettings : NavRoute("smart_collections_settings")
    data object ConnectedServices : NavRoute("connected_services")
}
