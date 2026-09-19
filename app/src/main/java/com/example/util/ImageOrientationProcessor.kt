package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.domain.model.MediaItem
import com.example.domain.repository.R2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TargetOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

data class ImageOrientationInfo(
    val currentOrientation: TargetOrientation,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val exifRotation: Int
)

/**
 * ImageOrientationProcessor
 *
 * Dedicated image processing engine that creates REAL physical image canvases
 * with opposite orientations (Portrait -> Landscape, Landscape -> Portrait).
 * Preserves 100% of original photo content without cropping or distortion,
 * using intelligent blurred background extension.
 */
object ImageOrientationProcessor {

    private const val MAX_PROCESSING_DIMENSION = 4096

    /**
     * Inspects image dimensions and EXIF metadata without loading full bitmap into memory.
     */
    suspend fun detectImageInfo(context: Context, item: MediaItem): ImageOrientationInfo = withContext(Dispatchers.IO) {
        var rawWidth = item.width
        var rawHeight = item.height
        var exifRotation = 0
        var mimeType = item.mimeType.ifEmpty { "image/jpeg" }

        val tempFile = copySourceToTemp(context, item)
        try {
            if (tempFile != null && tempFile.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempFile.absolutePath, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    rawWidth = options.outWidth
                    rawHeight = options.outHeight
                }
                if (!options.outMimeType.isNullOrEmpty()) {
                    mimeType = options.outMimeType
                }

                try {
                    val exif = ExifInterface(tempFile.absolutePath)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    exifRotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) { }
            }
        } finally {
            tempFile?.delete()
        }

        // Swap width and height if EXIF has 90 or 270 degrees rotation
        val effectiveWidth = if (exifRotation == 90 || exifRotation == 270) rawHeight else rawWidth
        val effectiveHeight = if (exifRotation == 90 || exifRotation == 270) rawWidth else rawHeight

        val currentOrientation = if (effectiveHeight >= effectiveWidth) {
            TargetOrientation.PORTRAIT
        } else {
            TargetOrientation.LANDSCAPE
        }

        ImageOrientationInfo(
            currentOrientation = currentOrientation,
            width = max(1, effectiveWidth),
            height = max(1, effectiveHeight),
            mimeType = mimeType,
            exifRotation = exifRotation
        )
    }

    /**
     * Determines physical target dimensions for the new canvas.
     */
    fun computeTargetDimensions(
        srcWidth: Int,
        srcHeight: Int,
        targetOrientation: TargetOrientation
    ): Pair<Int, Int> {
        val w = max(1, srcWidth)
        val h = max(1, srcHeight)

        val (targetW, targetH) = when (targetOrientation) {
            TargetOrientation.PORTRAIT -> {
                if (h > w) {
                    // Already portrait
                    Pair(w, h)
                } else if (w > h) {
                    // Landscape -> Portrait (e.g. 1920x1080 -> 1080x1920)
                    Pair(h, w)
                } else {
                    // Square -> standard portrait 9:16
                    Pair(w, (h * 16) / 9)
                }
            }
            TargetOrientation.LANDSCAPE -> {
                if (w > h) {
                    // Already landscape
                    Pair(w, h)
                } else if (h > w) {
                    // Portrait -> Landscape (e.g. 1080x1920 -> 1920x1080)
                    Pair(h, w)
                } else {
                    // Square -> standard landscape 16:9
                    Pair((w * 16) / 9, h)
                }
            }
        }

        // Cap dimensions at MAX_PROCESSING_DIMENSION to prevent OutOfMemoryError
        val maxDim = max(targetW, targetH)
        return if (maxDim > MAX_PROCESSING_DIMENSION) {
            val scale = MAX_PROCESSING_DIMENSION.toFloat() / maxDim
            Pair((targetW * scale).roundToInt(), (targetH * scale).roundToInt())
        } else {
            Pair(targetW, targetH)
        }
    }

    /**
     * Renders a real physical canvas with smart blurred background and proportional foreground.
     */
    fun renderOrientationCanvas(
        sourceBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val targetBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)

        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        // 1. Render smart blurred background
        // Downsample source to ~140px for superfast, smooth stack blur
        val smallW = 140
        val smallH = max(1, ((140f * srcH) / srcW).roundToInt())
        val smallBitmap = Bitmap.createScaledBitmap(sourceBitmap, smallW, smallH, true)
        val blurredSmall = FastBlur.blur(smallBitmap, radius = 18)

        // Draw blurred background scaled to cover the entire target canvas
        val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val targetRect = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
        canvas.drawBitmap(blurredSmall, null, targetRect, bgPaint)

        // Subtle dark translucent scrim for depth & focus on original photo
        canvas.drawColor(Color.argb(55, 0, 0, 0))

        // Clean up small blurred bitmap
        if (blurredSmall != smallBitmap) {
            blurredSmall.recycle()
        }
        smallBitmap.recycle()

        // 2. Render original photo fitted proportionally and centered
        val fitScale = min(targetWidth.toFloat() / srcW, targetHeight.toFloat() / srcH)
        val drawW = (srcW * fitScale).roundToInt()
        val drawH = (srcH * fitScale).roundToInt()
        val left = (targetWidth - drawW) / 2f
        val top = (targetHeight - drawH) / 2f
        val fgRect = RectF(left, top, left + drawW, top + drawH)

        // Subtle soft shadow behind photo
        try {
            val shadowPaint = Paint().apply {
                color = Color.argb(60, 0, 0, 0)
                maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRect(fgRect, shadowPaint)
        } catch (_: Exception) { }

        // Draw proportional, uncropped original content
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, null, fgRect, fgPaint)

        return targetBitmap
    }

    /**
     * Executes the full pipeline:
     * Decode -> Normalize EXIF -> Render Canvas -> Save to MediaStore or R2 -> Return updated MediaItem.
     */
    suspend fun processAndSaveOrientation(
        context: Context,
        item: MediaItem,
        targetOrientation: TargetOrientation,
        r2Repository: R2Repository? = null,
        onProgress: (String) -> Unit = {}
    ): Result<MediaItem> = withContext(Dispatchers.IO) {
        val actionText = if (targetOrientation == TargetOrientation.LANDSCAPE) "landscape" else "portrait"
        onProgress("Creating $actionText image…")

        val tempSourceFile = copySourceToTemp(context, item)
            ?: return@withContext Result.failure(IllegalStateException("Could not read original media item"))

        try {
            // Read EXIF orientation
            var exifRotation = 0
            var flipHorizontal = false
            try {
                val exif = ExifInterface(tempSourceFile.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> exifRotation = 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> exifRotation = 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> exifRotation = 270
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal = true
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        exifRotation = 90
                        flipHorizontal = true
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        exifRotation = 270
                        flipHorizontal = true
                    }
                }
            } catch (_: Exception) { }

            // Decode source bitmap safely with bounds sampling if needed
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempSourceFile.absolutePath, boundsOptions)
            val maxRawDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxRawDim / (sampleSize * 2) >= MAX_PROCESSING_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(tempSourceFile.absolutePath, decodeOptions)
                ?: return@withContext Result.failure(IllegalStateException("Failed to decode image bitmap"))

            // Normalize EXIF orientation
            val normalizedBitmap = normalizeBitmap(rawBitmap, exifRotation, flipHorizontal)

            // Compute target dimensions
            val (targetW, targetH) = computeTargetDimensions(
                srcWidth = normalizedBitmap.width,
                srcHeight = normalizedBitmap.height,
                targetOrientation = targetOrientation
            )

            // Render final canvas
            val resultBitmap = renderOrientationCanvas(
                sourceBitmap = normalizedBitmap,
                targetWidth = targetW,
                targetHeight = targetH
            )

            // Recycle normalized bitmap if different
            if (normalizedBitmap != rawBitmap) {
                normalizedBitmap.recycle()
            }
            rawBitmap.recycle()

            // Generate descriptive file name
            val baseName = item.name.substringBeforeLast('.')
            val extension = item.name.substringAfterLast('.', "jpg").lowercase()
            val newFileName = "${baseName}_${actionText}.$extension"

            // Save appropriately
            if (item.isCloud && r2Repository != null) {
                // Cloudflare R2 item
                onProgress("Uploading to Cloud Storage…")
                val tempOutputFile = File(context.cacheDir, "oriented_${System.currentTimeMillis()}.$extension")
                try {
                    FileOutputStream(tempOutputFile).use { fos ->
                        compressBitmap(resultBitmap, extension, fos)
                    }

                    val prefix = item.path.substringBeforeLast('/', "")
                    val uploadResult = FileInputStream(tempOutputFile).use { fis ->
                        r2Repository.uploadFile(
                            fileName = newFileName,
                            prefix = prefix,
                            inputStream = fis,
                            contentLength = tempOutputFile.length(),
                            mimeType = item.mimeType.ifEmpty { "image/jpeg" }
                        )
                    }

                    resultBitmap.recycle()

                    if (uploadResult.isSuccess) {
                        val key = uploadResult.getOrThrow()
                        val presignedUrl = r2Repository.getPresignedUrl(key).getOrNull()
                        val newCloudItem = MediaItem(
                            id = (key.hashCode().toLong() and 0x7FFFFFFFL) + 2_000_000_000L,
                            uriString = presignedUrl ?: item.uriString,
                            name = newFileName,
                            path = key,
                            size = tempOutputFile.length(),
                            dateAdded = System.currentTimeMillis() / 1000,
                            mimeType = item.mimeType.ifEmpty { "image/jpeg" },
                            isVideo = false,
                            width = targetW,
                            height = targetH,
                            albumName = "Cloud Storage",
                            isCloud = true,
                            cloudKey = key
                        )
                        Result.success(newCloudItem)
                    } else {
                        Result.failure(uploadResult.exceptionOrNull() ?: IllegalStateException("Cloud upload failed"))
                    }
                } finally {
                    tempOutputFile.delete()
                }
            } else {
                // Local MediaStore image
                onProgress("Saving to Gallery…")
                val savedItem = saveToMediaStore(
                    context = context,
                    bitmap = resultBitmap,
                    fileName = newFileName,
                    mimeType = item.mimeType.ifEmpty { "image/jpeg" },
                    targetWidth = targetW,
                    targetHeight = targetH
                )
                resultBitmap.recycle()
                Result.success(savedItem)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempSourceFile.delete()
        }
    }

    private fun normalizeBitmap(bitmap: Bitmap, degrees: Int, flipHorizontal: Boolean): Bitmap {
        if (degrees == 0 && !flipHorizontal) return bitmap
        val matrix = Matrix()
        if (degrees != 0) {
            matrix.postRotate(degrees.toFloat())
        }
        if (flipHorizontal) {
            matrix.postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return rotated
    }

    private fun compressBitmap(bitmap: Bitmap, extension: String, outputStream: java.io.OutputStream) {
        val format = when {
            extension.contains("png") -> Bitmap.CompressFormat.PNG
            extension.contains("webp") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 95
        bitmap.compress(format, quality, outputStream)
        outputStream.flush()
    }

    private fun saveToMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        mimeType: String,
        targetWidth: Int,
        targetHeight: Int
    ): MediaItem {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.WIDTH, targetWidth)
            put(MediaStore.Images.Media.HEIGHT, targetHeight)
            val nowSeconds = System.currentTimeMillis() / 1000
            put(MediaStore.Images.Media.DATE_ADDED, nowSeconds)
            put(MediaStore.Images.Media.DATE_MODIFIED, nowSeconds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CloudGallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        var bytesWritten = 0L
        context.contentResolver.openOutputStream(uri)?.use { os ->
            val extension = fileName.substringAfterLast('.', "jpg")
            compressBitmap(bitmap, extension, os)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }

        val id = try {
            ContentUris.parseId(uri)
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        // Trigger MediaScanner for immediate gallery synchronization
        try {
            MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), arrayOf(mimeType), null)
        } catch (_: Exception) { }

        return MediaItem(
            id = id,
            uriString = uri.toString(),
            name = fileName,
            path = uri.toString(),
            size = bytesWritten,
            dateAdded = System.currentTimeMillis() / 1000,
            dateModified = System.currentTimeMillis() / 1000,
            mimeType = mimeType,
            isVideo = false,
            width = targetWidth,
            height = targetHeight,
            albumName = "CloudGallery"
        )
    }

    private suspend fun copySourceToTemp(context: Context, item: MediaItem): File? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "source_${System.currentTimeMillis()}.tmp")
        try {
            val inputStream: InputStream? = when {
                item.uriString.startsWith("http://") || item.uriString.startsWith("https://") -> {
                    URL(item.uriString).openStream()
                }
                item.uriString.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(item.uriString))
                }
                item.uriString.startsWith("file://") -> {
                    FileInputStream(File(Uri.parse(item.uriString).path ?: item.path))
                }
                item.path.isNotEmpty() && File(item.path).exists() -> {
                    FileInputStream(File(item.path))
                }
                else -> {
                    context.contentResolver.openInputStream(Uri.parse(item.uriString))
                }
            }

            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }
}
