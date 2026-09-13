package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageFormatter {
    fun formatStorageSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val b = bytes.toDouble()
        val kb = b / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0

        return when {
            tb >= 1.0 -> String.format(Locale.US, "%.1f TB", tb)
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        if (diff < 0L || diff < 30_000L) return "Just now"
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 60 -> "$minutes min${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
