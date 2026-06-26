package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.ui.SheetUiData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporters {

    // Convert column index (0-based) to Excel alphabetical identifier (A, B, C, ...)
    private fun getColumnLabel(index: Int): String {
        var temp = index
        val label = StringBuilder()
        while (temp >= 0) {
            label.insert(0, ('A'.code + (temp % 26)).toChar())
            temp = temp / 26 - 1
        }
        return label.toString()
    }

    /**
     * Generates a fully-compliant multi-table spreadsheet in a single .xlsx package.
     */
    fun exportToExcel(
        context: Context,
        sheetsData: List<SheetUiData>
    ) {
        try {
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "grid_calculation_aggregate.xlsx")
            val fos = FileOutputStream(file)
            val zos = ZipOutputStream(fos)

            // 1. Write [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """.trimIndent()
            zos.write(contentTypes.toByteArray())
            zos.closeEntry()

            // 2. Write _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val docRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent()
            zos.write(docRels.toByteArray())
            zos.closeEntry()

            // 3. Write xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            val workbook = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets>
                        <sheet name="Grid Multi Sheet" sheetId="1" r:id="rId1"/>
                    </sheets>
                </workbook>
            """.trimIndent()
            zos.write(workbook.toByteArray())
            zos.closeEntry()

            // 4. Write xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            val workbookRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent()
            zos.write(workbookRels.toByteArray())
            zos.closeEntry()

            // 5. Generate and Write xl/worksheets/sheet1.xml (Cell data)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))

            val sheetData = StringBuilder()
            sheetData.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            sheetData.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
            sheetData.append("<sheetData>\n")

            var currentXlsxRow = 1
            var totalPiecesCombined = 0
            var grandTotalCombined = 0.0

            sheetsData.forEach { sheetDataObj ->
                val sheetName = sheetDataObj.sheet.name
                val columns = sheetDataObj.columns
                val rows = sheetDataObj.rows
                val cellsMap = sheetDataObj.cellsMap

                // Table Header / Title Row
                sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>=== ${escapeXml(sheetName)} ===</t></is></c>\n")
                sheetData.append("  </row>\n")
                currentXlsxRow++

                if (columns.isNotEmpty() || rows.isNotEmpty()) {
                    // Header row (Row 1 for this table)
                    sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                    sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Date</t></is></c>\n")
                    for (colIdx in columns.indices) {
                        val label = getColumnLabel(colIdx + 1) // B, C, D...
                        val colName = columns[colIdx].name
                        sheetData.append("    <c r=\"$label$currentXlsxRow\" t=\"inlineStr\"><is><t>${escapeXml(colName)}</t></is></c>\n")
                    }
                    val rowTotalHeaderLabel = getColumnLabel(columns.size + 1)
                    sheetData.append("    <c r=\"$rowTotalHeaderLabel$currentXlsxRow\" t=\"inlineStr\"><is><t>Row Total (Price)</t></is></c>\n")
                    sheetData.append("  </row>\n")
                    currentXlsxRow++

                    // Rates row
                    sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                    sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Rates / Prices</t></is></c>\n")
                    for (colIdx in columns.indices) {
                        val label = getColumnLabel(colIdx + 1)
                        val colRate = columns[colIdx].rate
                        sheetData.append("    <c r=\"$label$currentXlsxRow\" t=\"n\"><v>$colRate</v></c>\n")
                    }
                    sheetData.append("    <c r=\"$rowTotalHeaderLabel$currentXlsxRow\" t=\"inlineStr\"><is><t>N/A</t></is></c>\n")
                    sheetData.append("  </row>\n")
                    currentXlsxRow++

                    // Data rows
                    val colQtySum = IntArray(columns.size) { 0 }
                    val colPriceSum = DoubleArray(columns.size) { 0.0 }
                    var sheetPiecesCount = 0
                    var sheetTotalAmount = 0.0

                    for (row in rows) {
                        sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                        sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>${escapeXml(row.dateString)}</t></is></c>\n")

                        var rowTotalValue = 0.0
                        for (colIdx in columns.indices) {
                            val label = getColumnLabel(colIdx + 1)
                            val col = columns[colIdx]
                            val qty = cellsMap[Pair(row.id, col.id)] ?: 0
                            val price = qty * col.rate
                            rowTotalValue += price

                            colQtySum[colIdx] += qty
                            colPriceSum[colIdx] += price
                            sheetPiecesCount += qty

                            sheetData.append("    <c r=\"$label$currentXlsxRow\" t=\"n\"><v>$qty</v></c>\n")
                        }
                        sheetTotalAmount += rowTotalValue
                        totalPiecesCombined += sheetPiecesCount
                        grandTotalCombined += rowTotalValue

                        sheetData.append("    <c r=\"$rowTotalHeaderLabel$currentXlsxRow\" t=\"n\"><v>$rowTotalValue</v></c>\n")
                        sheetData.append("  </row>\n")
                        currentXlsxRow++
                    }

                    // Total Pieces row
                    sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                    sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Total Pieces</t></is></c>\n")
                    for (colIdx in columns.indices) {
                        val label = getColumnLabel(colIdx + 1)
                        sheetData.append("    <c r=\"$label$currentXlsxRow\" t=\"n\"><v>${colQtySum[colIdx]}</v></c>\n")
                    }
                    sheetData.append("    <c r=\"$rowTotalHeaderLabel$currentXlsxRow\" t=\"n\"><v>$sheetPiecesCount</v></c>\n")
                    sheetData.append("  </row>\n")
                    currentXlsxRow++

                    // Total Price row
                    sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                    sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Total Price</t></is></c>\n")
                    for (colIdx in columns.indices) {
                        val label = getColumnLabel(colIdx + 1)
                        sheetData.append("    <c r=\"$label$currentXlsxRow\" t=\"n\"><v>${colPriceSum[colIdx]}</v></c>\n")
                    }
                    sheetData.append("    <c r=\"$rowTotalHeaderLabel$currentXlsxRow\" t=\"n\"><v>$sheetTotalAmount</v></c>\n")
                    sheetData.append("  </row>\n")
                    currentXlsxRow++
                } else {
                    sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                    sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>(Empty Table)</t></is></c>\n")
                    sheetData.append("  </row>\n")
                    currentXlsxRow++
                }

                // Add blank separating row
                sheetData.append("  <row r=\"$currentXlsxRow\">\n")
                sheetData.append("  </row>\n")
                currentXlsxRow++
            }

            // Overall summary header
            sheetData.append("  <row r=\"$currentXlsxRow\">\n")
            sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>=== COMBINED SUMMARY ===</t></is></c>\n")
            sheetData.append("  </row>\n")
            currentXlsxRow++

            sheetData.append("  <row r=\"$currentXlsxRow\">\n")
            sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Total Connected Tables</t></is></c>\n")
            sheetData.append("    <c r=\"B$currentXlsxRow\" t=\"n\"><v>${sheetsData.size}</v></c>\n")
            sheetData.append("  </row>\n")
            currentXlsxRow++

            sheetData.append("  <row r=\"$currentXlsxRow\">\n")
            sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Combined Total Pieces</t></is></c>\n")
            sheetData.append("    <c r=\"B$currentXlsxRow\" t=\"n\"><v>$totalPiecesCombined</v></c>\n")
            sheetData.append("  </row>\n")
            currentXlsxRow++

            sheetData.append("  <row r=\"$currentXlsxRow\">\n")
            sheetData.append("    <c r=\"A$currentXlsxRow\" t=\"inlineStr\"><is><t>Combined Net Grand Total</t></is></c>\n")
            sheetData.append("    <c r=\"B$currentXlsxRow\" t=\"n\"><v>$grandTotalCombined</v></c>\n")
            sheetData.append("  </row>\n")

            sheetData.append("</sheetData>\n")
            sheetData.append("</worksheet>\n")

            zos.write(sheetData.toString().toByteArray())
            zos.closeEntry()

            zos.close()
            fos.close()

            shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Excel Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Generates a beautifully formatted PDF report of all tables dynamically flowing over multiple pages.
     */
    fun exportToPdf(
        context: Context,
        sheetsData: List<SheetUiData>
    ) {
        try {
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "grid_calculation_aggregate.pdf")

            val pdfDoc = PdfDocument()

            val pageWidth = 1000
            val pageHeight = 800

            // Base Paint setups
            val paintTitle = Paint().apply {
                color = AndroidColor.parseColor("#1E6C43") // Brand Forest Green
                textSize = 24f
                isAntiAlias = true
                isFakeBoldText = true
            }

            val paintSubtitle = Paint().apply {
                color = AndroidColor.DKGRAY
                textSize = 12f
                isAntiAlias = true
            }

            val paintSectionHeader = Paint().apply {
                color = AndroidColor.parseColor("#15432B")
                textSize = 16f
                isAntiAlias = true
                isFakeBoldText = true
            }

            val paintHeaderBackground = Paint().apply {
                color = AndroidColor.parseColor("#E8F5E9")
                style = Paint.Style.FILL
            }

            val paintHeaderBorder = Paint().apply {
                color = AndroidColor.parseColor("#1E6C43")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            val paintCellBorder = Paint().apply {
                color = AndroidColor.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val paintText = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 11f
                isAntiAlias = true
            }

            val paintTextBold = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 11f
                isAntiAlias = true
                isFakeBoldText = true
            }

            val paintHighlightedCell = Paint().apply {
                color = AndroidColor.parseColor("#D0EED9")
                style = Paint.Style.FILL
            }

            val dateColWidth = 110f
            val maxColWidth = 85f
            val startX = 40f
            val cellHeight = 28f

            var pageCount = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            // Overall calculated summary stats
            var combinedPiecesMerged = 0
            var combinedGrandTotalMoney = 0.0

            sheetsData.forEach { s ->
                s.rows.forEach { r ->
                    s.columns.forEach { c ->
                        val qty = s.cellsMap[Pair(r.id, c.id)] ?: 0
                        combinedPiecesMerged += qty
                        combinedGrandTotalMoney += qty * c.rate
                    }
                }
            }

            // Draw Document Cover title band on first page
            canvas.drawText("Production Tracking & Billing Report", startX, 45f, paintTitle)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            canvas.drawText("Generated on: ${format.format(Date())}  |  Connected Tables: ${sheetsData.size}", startX, 65f, paintSubtitle)

            // Ribbon cover metrics block
            val paintDbBg = Paint().apply {
                color = AndroidColor.parseColor("#F4FBF7")
                style = Paint.Style.FILL
            }
            val paintDbBorder = Paint().apply {
                color = AndroidColor.parseColor("#C8E6C9")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRect(startX, 80f, startX + 920f, 125f, paintDbBg)
            canvas.drawRect(startX, 80f, startX + 920f, 125f, paintDbBorder)

            val summaryMergedString = "COMBINED ACCOUNT GENERAL METRICS ->  Total Tables: ${sheetsData.size}   |   Combined Pieces: $combinedPiecesMerged   |   Combined Net Total: ${String.format("%.2f", combinedGrandTotalMoney)}"
            canvas.drawText(summaryMergedString, startX + 20f, 107f, paintTextBold)

            var currentY = 160f

            // Dynamic layout loop rendering each sheet sequentially with auto paging bounds checking
            sheetsData.forEach { sheetObj ->
                val sheetName = sheetObj.sheet.name
                val rows = sheetObj.rows
                val columns = sheetObj.columns
                val cellsMap = sheetObj.cellsMap
                val totalCols = columns.size + 2

                // Calculate horizontal column widths for this specific sheet
                val dynamicColWidth = if (totalCols > 2) {
                    val remainingWidth = 920f - dateColWidth
                    val widthPerCol = remainingWidth / (totalCols - 1)
                    if (widthPerCol > maxColWidth) maxColWidth else widthPerCol
                } else {
                    maxColWidth
                }

                // Check and allocate new page if not enough canvas room remains to draw sheet header + at least 2 rows
                val sheetHeaderHeightNeeded = 110f
                if (currentY + sheetHeaderHeightNeeded > pageHeight - 60f) {
                    pdfDoc.finishPage(page)
                    pageCount++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 50f
                }

                // Draw Local Section/Sheet Title
                canvas.drawText("Table: $sheetName", startX, currentY + 18f, paintSectionHeader)
                currentY += 30f

                if (columns.isNotEmpty() || rows.isNotEmpty()) {
                    // Draw Headers with border outline
                    canvas.drawRect(startX, currentY, startX + 920f, currentY + cellHeight * 2f, paintHeaderBackground)
                    canvas.drawRect(startX, currentY, startX + 920f, currentY + cellHeight * 2f, paintHeaderBorder)

                    // Header Line 1: Column Names
                    canvas.drawText("Date", startX + 10f, currentY + 20f, paintTextBold)
                    var stepX = startX + dateColWidth
                    for (col in columns) {
                        val clipLabel = if (col.name.length > 12) col.name.take(11) + ".." else col.name
                        canvas.drawText(clipLabel, stepX + 8f, currentY + 20f, paintTextBold)
                        stepX += dynamicColWidth
                    }
                    canvas.drawText("Row Total", stepX + 8f, currentY + 20f, paintTextBold)

                    // Header Line 2: Rates
                    currentY += cellHeight
                    canvas.drawText("Rates / Prices", startX + 10f, currentY + 20f, paintSubtitle)
                    stepX = startX + dateColWidth
                    for (col in columns) {
                        canvas.drawText("${col.rate}", stepX + 8f, currentY + 20f, paintSubtitle)
                        stepX += dynamicColWidth
                    }
                    canvas.drawText("(Price)", stepX + 8f, currentY + 20f, paintSubtitle)

                    currentY += cellHeight

                    // Draw Data Rows
                    val colQtySum = IntArray(columns.size) { 0 }
                    val colPriceSum = DoubleArray(columns.size) { 0.0 }
                    var sheetPiecesCount = 0
                    var sheetGrandTotal = 0.0

                    for (row in rows) {
                        // Check row overflow
                        if (currentY + cellHeight > pageHeight - 100f) {
                            pdfDoc.finishPage(page)
                            pageCount++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                            page = pdfDoc.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = 50f

                            // Re-anchor headers for continuity on next page
                            canvas.drawRect(startX, currentY, startX + 920f, currentY + cellHeight, paintHeaderBackground)
                            canvas.drawText("Date (Continued - $sheetName)", startX + 10f, currentY + 18f, paintTextBold)
                            stepX = startX + dateColWidth
                            for (col in columns) {
                                val clipLabel = if (col.name.length > 12) col.name.take(11) + ".." else col.name
                                canvas.drawText(clipLabel, stepX + 8f, currentY + 18f, paintTextBold)
                                stepX += dynamicColWidth
                            }
                            canvas.drawText("Row Total", stepX + 8f, currentY + 18f, paintTextBold)
                            currentY += cellHeight
                        }

                        canvas.drawLine(startX, currentY, startX + 920f, currentY, paintCellBorder)
                        canvas.drawLine(startX, currentY + cellHeight, startX + 920f, currentY + cellHeight, paintCellBorder)

                        // Format & Draw Row Date Label
                        canvas.drawText(row.dateString, startX + 10f, currentY + 18f, paintText)

                        stepX = startX + dateColWidth
                        var rTotal = 0.0
                        for (colIdx in columns.indices) {
                            val col = columns[colIdx]
                            val qty = cellsMap[Pair(row.id, col.id)] ?: 0
                            val cellPrice = qty * col.rate
                            rTotal += cellPrice

                            colQtySum[colIdx] += qty
                            colPriceSum[colIdx] += cellPrice
                            sheetPiecesCount += qty

                            if (qty > 0) {
                                canvas.drawRect(stepX + 1f, currentY + 1f, stepX + dynamicColWidth - 1f, currentY + cellHeight - 1f, paintHighlightedCell)
                                canvas.drawText(qty.toString(), stepX + 10f, currentY + 18f, paintTextBold)
                            } else {
                                canvas.drawText("-", stepX + 10f, currentY + 18f, paintSubtitle)
                            }
                            stepX += dynamicColWidth
                        }

                        sheetGrandTotal += rTotal
                        canvas.drawText(String.format("%.2f", rTotal), stepX + 8f, currentY + 18f, paintTextBold)
                        currentY += cellHeight
                    }

                    // Render Local Table Totals block
                    if (currentY + cellHeight * 2f > pageHeight - 65f) {
                        pdfDoc.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                        page = pdfDoc.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = 50f
                    }

                    canvas.drawLine(startX, currentY, startX + 920f, currentY, paintHeaderBorder)

                    // Sheet Local Total Pieces
                    canvas.drawText("Total Pieces", startX + 10f, currentY + 18f, paintTextBold)
                    stepX = startX + dateColWidth
                    for (colIdx in columns.indices) {
                        canvas.drawText(colQtySum[colIdx].toString(), stepX + 8f, currentY + 18f, paintTextBold)
                        stepX += dynamicColWidth
                    }
                    canvas.drawText(sheetPiecesCount.toString(), stepX + 8f, currentY + 18f, paintTextBold)

                    currentY += cellHeight
                    canvas.drawLine(startX, currentY, startX + 920f, currentY, paintCellBorder)

                    // Sheet Local Total Price
                    canvas.drawText("Total Price", startX + 10f, currentY + 18f, paintTextBold)
                    stepX = startX + dateColWidth
                    for (colIdx in columns.indices) {
                        canvas.drawText(String.format("%.2f", colPriceSum[colIdx]), stepX + 8f, currentY + 18f, paintTextBold)
                        stepX += dynamicColWidth
                    }
                    canvas.drawText(String.format("%.2f", sheetGrandTotal), stepX + 8f, currentY + 18f, paintTextBold)

                    currentY += cellHeight
                    canvas.drawLine(startX, currentY, startX + 920f, currentY, paintHeaderBorder)
                } else {
                    // Empty sheet placeholder line
                    canvas.drawText("(No columns or rows configured inside this table yet)", startX + 15f, currentY + 15f, paintSubtitle)
                    currentY += cellHeight + 15f
                }

                // Generous vertical safety gap between adjacent sheets
                currentY += 40f
            }

            pdfDoc.finishPage(page)

            // Deliver output
            val fos = FileOutputStream(file)
            pdfDoc.writeTo(fos)
            fos.close()
            pdfDoc.close()

            shareFile(context, file, "application/pdf")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Calculations Export Summary")
            putExtra(Intent.EXTRA_TEXT, "Here is your multi-table connected production grid export report.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Export File"))
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
