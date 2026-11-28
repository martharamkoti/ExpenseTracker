package com.ramkotimartha.expensetracker.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class ExportRangeSummary(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val matchingTransactions: List<TransactionWithTag>,
    val expenseTransactions: List<TransactionWithTag>,
    val incomeTransactions: List<TransactionWithTag>,
    val expenseCategories: List<Tag>,
    val incomeCategories: List<Tag>,
    val totalExpense: Double,
    val totalIncome: Double
) {
    val totalRecordsCount: Int get() = matchingTransactions.size
    val netBalance: Double get() = totalIncome - totalExpense
}

/**
 * Deterministic date-range filtering plus native CSV and multi-page A4 PDF report generation.
 */
object ReportExporter {

    private val CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    private val DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

    fun buildRangeSummary(
        allTransactions: List<TransactionWithTag>,
        fromDate: LocalDate,
        toDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExportRangeSummary {
        val effectiveStart = if (fromDate <= toDate) fromDate else toDate
        val effectiveEnd = if (fromDate <= toDate) toDate else fromDate

        val matching = allTransactions
            .filter { item ->
                val date = Instant.ofEpochMilli(item.transaction.timestamp)
                    .atZone(zoneId)
                    .toLocalDate()
                !date.isBefore(effectiveStart) && !date.isAfter(effectiveEnd)
            }
            .sortedWith(
                compareByDescending<TransactionWithTag> { it.transaction.timestamp }
                    .thenByDescending { it.transaction.id }
            )

        val expenses = matching.filter { it.transaction.type == TransactionType.EXPENSE }
        val incomes = matching.filter { it.transaction.type == TransactionType.INCOME }

        val expenseTags = expenses
            .groupBy { it.tag.id }
            .values
            .sortedByDescending { group -> group.sumOf { it.transaction.amount } }
            .map { it.first().tag }

        val incomeTags = incomes
            .groupBy { it.tag.id }
            .values
            .sortedByDescending { group -> group.sumOf { it.transaction.amount } }
            .map { it.first().tag }

        return ExportRangeSummary(
            fromDate = effectiveStart,
            toDate = effectiveEnd,
            matchingTransactions = matching,
            expenseTransactions = expenses,
            incomeTransactions = incomes,
            expenseCategories = expenseTags,
            incomeCategories = incomeTags,
            totalExpense = expenses.sumOf { it.transaction.amount },
            totalIncome = incomes.sumOf { it.transaction.amount }
        )
    }

    fun suggestedFileName(fromDate: LocalDate, toDate: LocalDate, extension: String): String {
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        return "MoneyTrack_Report_${fromDate.format(FILE_DATE_FORMAT)}_${toDate.format(FILE_DATE_FORMAT)}$ext"
    }

    fun generateCsvContent(
        summary: ExportRangeSummary,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()
        // UTF-8 BOM for seamless Excel / Google Sheets symbol rendering
        sb.append('\uFEFF')
        sb.appendLine("Date,Type,Category,Amount (INR),Note")

        for (item in summary.matchingTransactions) {
            val dateStr = Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(zoneId)
                .toLocalDate()
                .format(CSV_DATE_FORMAT)
            val typeStr = item.transaction.type.name
            val categoryStr = escapeCsvCell(item.tag.name)
            val amountStr = CurrencyUtils.formatRawAmount(item.transaction.amount)
            val noteStr = escapeCsvCell(item.transaction.note.orEmpty())
            sb.appendLine("$dateStr,$typeStr,$categoryStr,$amountStr,$noteStr")
        }

        sb.appendLine()
        sb.appendLine("Summary,,,,")
        sb.appendLine("Period,${escapeCsvCell("${summary.fromDate.format(CSV_DATE_FORMAT)} to ${summary.toDate.format(CSV_DATE_FORMAT)}")},,,")
        sb.appendLine("Total Income,,,${CurrencyUtils.formatRawAmount(summary.totalIncome)},")
        sb.appendLine("Total Expense,,,${CurrencyUtils.formatRawAmount(summary.totalExpense)},")
        sb.appendLine("Net Balance,,,${CurrencyUtils.formatRawAmount(summary.netBalance)},")

        return sb.toString()
    }

    private fun escapeCsvCell(raw: String): String {
        val cleaned = raw.replace("\r\n", " ").replace("\n", " ").trim()
        // Guard against spreadsheet formula injection while preserving clean text
        val safe = if (cleaned.isNotEmpty() && cleaned[0] in charArrayOf('=', '+', '-', '@')) {
            "'$cleaned"
        } else {
            cleaned
        }
        return if (safe.contains(',') || safe.contains('"')) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }

    suspend fun writeCsvToUri(
        context: Context,
        targetUri: Uri,
        summary: ExportRangeSummary
    ) = withContext(Dispatchers.IO) {
        val csv = generateCsvContent(summary)
        context.contentResolver.openOutputStream(targetUri, "w")?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("Unable to write CSV file.")
    }

    suspend fun writePdfToUri(
        context: Context,
        targetUri: Uri,
        summary: ExportRangeSummary
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(targetUri, "w")?.use { out ->
            writePdfToStream(out, summary)
        } ?: throw IllegalStateException("Unable to write PDF file.")
    }

    internal fun writePdfToStream(
        outputStream: OutputStream,
        summary: ExportRangeSummary,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - margin * 2

        val pdfDocument = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 100, 108)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(66, 52, 130)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 70, 80)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 46)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val boldCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 46)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 243, 250)
            style = Paint.Style.FILL
        }
        val headerRowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 235, 246)
            style = Paint.Style.FILL
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 225, 232)
            strokeWidth = 0.8f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(140, 140, 148)
            textSize = 9f
        }

        var pageNumber = 1
        var currentPage = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas: Canvas = currentPage.canvas
        var y = margin + 16f

        fun drawPageFooter(c: Canvas, pageNum: Int) {
            c.drawText(
                "Generated by MoneyTrack · Page $pageNum",
                margin,
                pageHeight - 22f,
                footerPaint
            )
        }

        fun startNewPage() {
            drawPageFooter(canvas, pageNumber)
            pdfDocument.finishPage(currentPage)
            pageNumber++
            currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = currentPage.canvas
            y = margin + 16f
        }

        try {
            // Header
            canvas.drawText("MoneyTrack — Expense & Income Report", margin, y, titlePaint)
            y += 18f
            val rangeLabel = "${summary.fromDate.format(DISPLAY_DATE_FORMAT)} – ${summary.toDate.format(DISPLAY_DATE_FORMAT)} · ${summary.totalRecordsCount} records"
            canvas.drawText(rangeLabel, margin, y, subtitlePaint)
            y += 22f

            // 3 KPI Summary Boxes
            val boxGap = 10f
            val boxWidth = (contentWidth - boxGap * 2) / 3f
            val boxHeight = 52f

            fun drawKpiBox(index: Int, label: String, amountText: String, valueColor: Int) {
                val left = margin + index * (boxWidth + boxGap)
                val rect = RectF(left, y, left + boxWidth, y + boxHeight)
                canvas.drawRoundRect(rect, 8f, 8f, cardFillPaint)
                canvas.drawText(label, left + 12f, y + 20f, subtitlePaint)
                val kpiValuePaint = Paint(boldCellPaint).apply {
                    color = valueColor
                    textSize = 13f
                }
                canvas.drawText(amountText, left + 12f, y + 40f, kpiValuePaint)
            }

            drawKpiBox(0, "Total Income", CurrencyUtils.formatCurrency(summary.totalIncome), Color.rgb(46, 125, 50))
            drawKpiBox(1, "Total Expense", CurrencyUtils.formatCurrency(summary.totalExpense), Color.rgb(198, 40, 40))
            val netColor = if (summary.netBalance >= 0) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40)
            drawKpiBox(2, "Net Balance", CurrencyUtils.formatCurrency(summary.netBalance), netColor)
            y += boxHeight + 26f

            // Category Breakdown Section (if expenses exist)
            if (summary.expenseTransactions.isNotEmpty()) {
                canvas.drawText("Expense Breakdown by Category", margin, y, sectionPaint)
                y += 10f

                val catHeaderRect = RectF(margin, y, margin + contentWidth, y + 22f)
                canvas.drawRoundRect(catHeaderRect, 4f, 4f, headerRowFillPaint)
                canvas.drawText("Category", margin + 10f, y + 15f, tableHeaderPaint)
                canvas.drawText("Records", margin + 240f, y + 15f, tableHeaderPaint)
                canvas.drawText("Share", margin + 330f, y + 15f, tableHeaderPaint)
                canvas.drawText("Amount", margin + 415f, y + 15f, tableHeaderPaint)
                y += 22f

                val groupedByTag = summary.expenseTransactions
                    .groupBy { it.tag }
                    .entries
                    .sortedByDescending { entry -> entry.value.sumOf { it.transaction.amount } }

                for ((tag, items) in groupedByTag) {
                    if (y > pageHeight - 70f) startNewPage()
                    val catTotal = items.sumOf { it.transaction.amount }
                    val pct = if (summary.totalExpense > 0.0) {
                        ((catTotal / summary.totalExpense) * 100.0).roundToInt()
                    } else 0

                    y += 16f
                    canvas.drawText(truncateText(tag.name, 30), margin + 10f, y, boldCellPaint)
                    canvas.drawText("${items.size}", margin + 240f, y, cellPaint)
                    canvas.drawText("$pct%", margin + 330f, y, cellPaint)
                    canvas.drawText(CurrencyUtils.formatCurrency(catTotal), margin + 415f, y, boldCellPaint)
                    y += 6f
                    canvas.drawLine(margin, y, margin + contentWidth, y, dividerPaint)
                }
                y += 24f
            }

            // Detailed Transactions Table
            if (y > pageHeight - 100f) startNewPage()
            canvas.drawText("Transactions (${summary.totalRecordsCount})", margin, y, sectionPaint)
            y += 10f

            fun drawTransactionsHeader() {
                val headerRect = RectF(margin, y, margin + contentWidth, y + 22f)
                canvas.drawRoundRect(headerRect, 4f, 4f, headerRowFillPaint)
                canvas.drawText("Date", margin + 8f, y + 15f, tableHeaderPaint)
                canvas.drawText("Category", margin + 92f, y + 15f, tableHeaderPaint)
                canvas.drawText("Note", margin + 205f, y + 15f, tableHeaderPaint)
                canvas.drawText("Amount", margin + 415f, y + 15f, tableHeaderPaint)
                y += 22f
            }

            drawTransactionsHeader()

            for (item in summary.matchingTransactions) {
                if (y > pageHeight - 56f) {
                    startNewPage()
                    drawTransactionsHeader()
                }
                y += 16f
                val dateStr = Instant.ofEpochMilli(item.transaction.timestamp)
                    .atZone(zoneId)
                    .toLocalDate()
                    .format(DISPLAY_DATE_FORMAT)
                val isExp = item.transaction.type == TransactionType.EXPENSE
                val sign = if (isExp) "-" else "+"
                val amtPaint = Paint(boldCellPaint).apply {
                    color = if (isExp) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50)
                }

                canvas.drawText(dateStr, margin + 8f, y, cellPaint)
                canvas.drawText(truncateText(item.tag.name, 18), margin + 92f, y, boldCellPaint)
                canvas.drawText(truncateText(item.transaction.note ?: "—", 34), margin + 205f, y, cellPaint)
                canvas.drawText("$sign${CurrencyUtils.formatCurrency(item.transaction.amount)}", margin + 415f, y, amtPaint)
                y += 6f
                canvas.drawLine(margin, y, margin + contentWidth, y, dividerPaint)
            }

            drawPageFooter(canvas, pageNumber)
            pdfDocument.finishPage(currentPage)
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun truncateText(text: String, maxChars: Int): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= maxChars) clean else clean.take(maxChars - 1) + "…"
    }
}
