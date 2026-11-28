package com.ramkotimartha.expensetracker.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import com.ramkotimartha.expensetracker.utils.CrashlyticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScannedReceiptResult(
    val imageUri: Uri,
    val previewBitmap: Bitmap?,
    val extractedData: ExtractedReceiptData
)

/**
 * Coordinates on-device ML Kit Text Recognition, EXIF-aware bitmap preview loading,
 * and temporary FileProvider URI management for camera receipt capture.
 */
object ReceiptScanner {

    private const val RECEIPT_CACHE_DIR = "receipt_scans"
    private const val MAX_PREVIEW_DIMENSION = 1280

    fun createTempPictureUri(context: Context): Uri {
        val dir = File(context.cacheDir, RECEIPT_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun clearCachedReceipts(context: Context) {
        try {
            val dir = File(context.cacheDir, RECEIPT_CACHE_DIR)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {
            // Best-effort cache cleanup
        }
    }

    suspend fun scanReceipt(
        context: Context,
        imageUri: Uri,
        availableTags: List<Tag>,
        pastTransactions: List<TransactionWithTag>
    ): ScannedReceiptResult = withContext(Dispatchers.IO) {
        val previewBitmap = decodeSampledBitmapWithExif(context, imageUri)
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val visionText = recognizeText(inputImage)

        val positionedElements = visionText.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox
                if (box != null) {
                    PositionedTextElement(
                        text = line.text,
                        left = box.left,
                        top = box.top,
                        bottom = box.bottom
                    )
                } else {
                    null
                }
            }
        }

        val extracted = if (positionedElements.isNotEmpty()) {
            ReceiptParser.parseFromPositionedLines(
                elements = positionedElements,
                availableTags = availableTags,
                pastTransactions = pastTransactions
            )
        } else {
            val rawLines = visionText.text.lines()
            ReceiptParser.parseFromLines(
                lines = rawLines,
                availableTags = availableTags,
                pastTransactions = pastTransactions
            )
        }

        ScannedReceiptResult(
            imageUri = imageUri,
            previewBitmap = previewBitmap,
            extractedData = extracted
        )
    }

    private suspend fun recognizeText(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            continuation.invokeOnCancellation {
                recognizer.close()
            }
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    recognizer.close()
                    CrashlyticsLogger.log(error, "On-device receipt OCR failed")
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }

    private fun decodeSampledBitmapWithExif(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            var inSampleSize = 1
            while ((width / inSampleSize) > MAX_PREVIEW_DIMENSION ||
                (height / inSampleSize) > MAX_PREVIEW_DIMENSION
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(
                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                )
                if (rotated !== rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            CrashlyticsLogger.log(e, "Failed to decode receipt preview bitmap")
            null
        }
    }
}
