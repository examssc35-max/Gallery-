package com.example

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.FastBlur
import com.example.util.ImageOrientationProcessor
import com.example.util.TargetOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImageOrientationTest {

    @Test
    fun testPortraitToLandscapeTargetDimensions() {
        val (targetW, targetH) = ImageOrientationProcessor.computeTargetDimensions(
            srcWidth = 1080,
            srcHeight = 1920,
            targetOrientation = TargetOrientation.LANDSCAPE
        )
        assertEquals(1920, targetW)
        assertEquals(1080, targetH)
        assertTrue("Landscape width must exceed height", targetW > targetH)
    }

    @Test
    fun testLandscapeToPortraitTargetDimensions() {
        val (targetW, targetH) = ImageOrientationProcessor.computeTargetDimensions(
            srcWidth = 1920,
            srcHeight = 1080,
            targetOrientation = TargetOrientation.PORTRAIT
        )
        assertEquals(1080, targetW)
        assertEquals(1920, targetH)
        assertTrue("Portrait height must exceed width", targetH > targetW)
    }

    @Test
    fun testHighResolutionDimensionsPreserved() {
        val (targetW, targetH) = ImageOrientationProcessor.computeTargetDimensions(
            srcWidth = 3000,
            srcHeight = 4000,
            targetOrientation = TargetOrientation.LANDSCAPE
        )
        assertEquals(4000, targetW)
        assertEquals(3000, targetH)
        assertTrue(targetW > targetH)
    }

    @Test
    fun testSquareDimensions() {
        val (landscapeW, landscapeH) = ImageOrientationProcessor.computeTargetDimensions(
            srcWidth = 1080,
            srcHeight = 1080,
            targetOrientation = TargetOrientation.LANDSCAPE
        )
        assertTrue("Landscape target must have width > height", landscapeW > landscapeH)

        val (portraitW, portraitH) = ImageOrientationProcessor.computeTargetDimensions(
            srcWidth = 1080,
            srcHeight = 1080,
            targetOrientation = TargetOrientation.PORTRAIT
        )
        assertTrue("Portrait target must have height > width", portraitH > portraitW)
    }

    @Test
    fun testFastBlurProducesValidBitmap() {
        val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)

        val blurred = FastBlur.blur(bitmap, radius = 5)
        assertNotNull(blurred)
        assertEquals(60, blurred.width)
        assertEquals(60, blurred.height)
    }

    @Test
    fun testRenderOrientationCanvasLandscape() {
        // Source is portrait: 100 x 200
        val sourceBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        sourceBitmap.eraseColor(android.graphics.Color.RED)

        val resultCanvas = ImageOrientationProcessor.renderOrientationCanvas(
            sourceBitmap = sourceBitmap,
            targetWidth = 200,
            targetHeight = 100
        )

        assertNotNull(resultCanvas)
        assertEquals(200, resultCanvas.width)
        assertEquals(100, resultCanvas.height)
        assertTrue("Resulting canvas must be landscape", resultCanvas.width > resultCanvas.height)
    }

    @Test
    fun testRenderOrientationCanvasPortrait() {
        // Source is landscape: 200 x 100
        val sourceBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        sourceBitmap.eraseColor(android.graphics.Color.GREEN)

        val resultCanvas = ImageOrientationProcessor.renderOrientationCanvas(
            sourceBitmap = sourceBitmap,
            targetWidth = 100,
            targetHeight = 200
        )

        assertNotNull(resultCanvas)
        assertEquals(100, resultCanvas.width)
        assertEquals(200, resultCanvas.height)
        assertTrue("Resulting canvas must be portrait", resultCanvas.height > resultCanvas.width)
    }
}
