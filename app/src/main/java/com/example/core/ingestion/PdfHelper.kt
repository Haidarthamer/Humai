package com.example.core.ingestion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PdfHelper(private val context: Context) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null

    companion object {
        private const val TAG = "PdfHelper"
        @Volatile private var pdfBoxInitialized = false
    }

    private fun ensurePdfBoxInitialized() {
        if (pdfBoxInitialized) return
        synchronized(PdfHelper::class.java) {
            if (!pdfBoxInitialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                pdfBoxInitialized = true
            }
        }
    }

    /** Copy a content URI into a unique, seekable cache file. */
    suspend fun cacheUriToLocalFile(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "document_cache").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
        val destFile = File(cacheDir, "${UUID.randomUUID()}_${safeName.ifBlank { "document" }}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected document.")
        input.use { source ->
            FileOutputStream(destFile).use { output -> source.copyTo(output) }
        }
        if (!destFile.isFile || destFile.length() == 0L) {
            destFile.delete()
            throw IllegalArgumentException("The selected document is empty or could not be copied.")
        }
        destFile
    }

    /** Opens a local PDF for the existing page viewer. */
    @Synchronized
    fun openPdf(file: File): Int {
        close()
        return try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = descriptor
            val renderer = PdfRenderer(descriptor)
            pdfRenderer = renderer
            renderer.pageCount
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open PDF: ${error.message}", error)
            close()
            0
        }
    }

    @Synchronized
    fun getPageCount(): Int = pdfRenderer?.pageCount ?: 0

    /** Render a zero-based page for the reader UI. */
    @Synchronized
    fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex !in 0 until renderer.pageCount || targetWidth <= 0) return null
        val page = try {
            renderer.openPage(pageIndex)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open PDF page $pageIndex", error)
            return null
        }

        return try {
            val aspect = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (error: Exception) {
            Log.e(TAG, "Unable to render PDF page $pageIndex", error)
            null
        } finally {
            page.close()
        }
    }

    /**
     * Extract selectable text using PDFBox's PDF parser, preserving real page
     * boundaries. It never fabricates placeholder sentences for scanned PDFs.
     */
    suspend fun extractTextByPages(file: File): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        require(file.isFile && file.canRead()) { "PDF file is missing or unreadable." }
        ensurePdfBoxInitialized()

        PDDocument.load(file).use { document ->
            require(document.numberOfPages > 0) { "PDF contains no pages." }
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                wordSeparator = " "
            }

            (1..document.numberOfPages).map { pageNumber ->
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                val extracted = stripper.getText(document)
                    .replace('\u0000', ' ')
                    .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                    .replace(Regex(" *\\n *"), "\n")
                    .trim()
                pageNumber to extracted
            }
        }
    }

    @Synchronized
    fun close() {
        try {
            pdfRenderer?.close()
        } catch (error: Exception) {
            Log.w(TAG, "Error closing PdfRenderer: ${error.message}")
        }
        try {
            fileDescriptor?.close()
        } catch (error: Exception) {
            Log.w(TAG, "Error closing ParcelFileDescriptor: ${error.message}")
        }
        pdfRenderer = null
        fileDescriptor = null
    }
}
