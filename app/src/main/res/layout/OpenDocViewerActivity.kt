/*
 *          ~ OPENDOC: Privacy-First Document Reader
 *    ~ Copyright (C) 2026 AamirForAi & OpenDoc Contributors
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

package com.gitlab.mudlej.MjPdfReader

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.opencsv.CSVReader
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

class OpenDocViewerActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var textContainer: NestedScrollView
    private lateinit var tableContainer: NestedScrollView
    private lateinit var contentViewText: TextView
    private lateinit var contentViewTable: TableLayout
    private lateinit var pageIndicator: TextView

    private var currentFileUri: Uri? = null
    private var fileExtension: String = ""
    private var filePassword: String? = null

    // Zoom setup parameters (Max limit 300% / 3x constraint)
    private var scaleFactor = 1.0f
    private val maxZoomLimit = 3.0f
    private val minZoomLimit = 0.8f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Virtual page navigation rules
    private var totalVirtualPages = 1
    private var currentVirtualPage = 1
    private val linesPerPageThreshold = 45

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opendoc_viewer)

        toolbar = findViewById(R.id.opendoc_toolbar)
        textContainer = findViewById(R.id.opendoc_scroll_wrapper)
        tableContainer = findViewById(R.id.opendoc_spreadsheet_scroll)
        contentViewText = findViewById(R.id.opendoc_text_render_view)
        contentViewTable = findViewById(R.id.opendoc_tabular_render_grid)
        pageIndicator = findViewById(R.id.opendoc_page_index_status)
        val btnGotoPage: View = findViewById(R.id.opendoc_btn_goto_page)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        currentFileUri = intent.data
        if (currentFileUri != null) {
            fileExtension = getFileExtension(currentFileUri!!)
            interceptAndLoadFile()
        } else {
            Toast.makeText(this, "Failed to resolve incoming document path", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnGotoPage.setOnClickListener {
            displayGoToPageDialogBox()
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(minZoomLimit, min(scaleFactor, maxZoomLimit))

                if (textContainer.visibility == View.VISIBLE) {
                    contentViewText.textSize = 16f * scaleFactor
                } else if (tableContainer.visibility == View.VISIBLE) {
                    for (i in 0 until contentViewTable.childCount) {
                        val row = contentViewTable.getChildAt(i) as? TableRow ?: continue
                        for (j in 0 until row.childCount) {
                            val tv = row.getChildAt(j) as? TextView ?: continue
                            tv.textSize = 14f * scaleFactor
                        }
                    }
                }
                return true
            }
        })

        textContainer.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
        }
        tableContainer.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun getFileExtension(uri: Uri): String {
        val path = uri.path ?: return ""
        val lastDot = path.lastIndexOf('.')
        return if (lastDot != -1) path.substring(lastDot + 1).lowercase() else ""
    }

    private fun interceptAndLoadFile() {
        try {
            when (fileExtension) {
                "txt" -> renderPlainTxt()
                "json" -> renderFormattedJson()
                "csv" -> renderTabularCsv()
                "docx", "doc", "xlsx", "xls", "pptx", "ppt" -> executeOfficeEngineParser()
                else -> {
                    Toast.makeText(this, "Unsupported document signature format", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            evaluateFilePropertiesMetadata()
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.cause?.message?.contains("password", ignoreCase = true) == true
            ) {
                promptForFilePassword()
            } else {
                Toast.makeText(this, "Engine failure reading payload metrics", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun promptForFilePassword() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Password Protected File")
        builder.setMessage("This local file is encrypted. Enter password to securely decode storage streams:")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("Decrypt") { dialog, _ ->
            filePassword = input.text.toString()
            dialog.dismiss()
            interceptAndLoadFile()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
            finish()
        }
        builder.show()
    }

    private fun renderPlainTxt() {
        tableContainer.visibility = View.GONE
        textContainer.visibility = View.VISIBLE

        val stream = contentResolver.openInputStream(currentFileUri!!) ?: return
        val reader = BufferedReader(InputStreamReader(stream))
        val builder = StringBuilder()

        reader.useLines { lines ->
            lines.forEach { line ->
                builder.append(line).append("\n")
            }
        }
        contentViewText.text = builder.toString()
        pageIndicator.text = "Document Structure: Text Stream"
        totalVirtualPages = 1
    }

    private fun renderFormattedJson() {
        tableContainer.visibility = View.GONE
        textContainer.visibility = View.VISIBLE

        val stream = contentResolver.openInputStream(currentFileUri!!) ?: return
        val reader = BufferedReader(InputStreamReader(stream))
        val rawText = reader.use { it.readText() }

        try {
            val jsonElement = JsonParser.parseString(rawText)
            val prettyGson = GsonBuilder().setPrettyPrinting().create()
            contentViewText.text = prettyGson.toJson(jsonElement)
        } catch (je: Exception) {
            contentViewText.text = rawText
        }
        pageIndicator.text = "Document Structure: JSON Node"
        totalVirtualPages = 1
    }

    private fun renderTabularCsv() {
        textContainer.visibility = View.GONE
        tableContainer.visibility = View.VISIBLE
        contentViewTable.removeAllViews()

        val stream = contentResolver.openInputStream(currentFileUri!!) ?: return
        val csvReader = CSVReader(InputStreamReader(stream))

        var nextLine: Array<String>?
        var isHeader = true

        csvReader.use { reader ->
            while (reader.readNext().also { nextLine = it } != null) {
                val row = TableRow(this)
                nextLine?.forEach { cellText ->
                    val tv = TextView(this)
                    tv.text = cellText
                    tv.setPadding(12, 8, 12, 8)
                    if (isHeader) {
                        tv.setTypeface(null, Typeface.BOLD)
                        tv.setBackgroundColor(Color.LTGRAY)
                    }
                    row.addView(tv)
                }
                isHeader = false
                contentViewTable.addView(row)
            }
        }
        pageIndicator.text = "Data Structure: Grid Matrix"
        totalVirtualPages = 1
    }

    private fun executeOfficeEngineParser() {
        tableContainer.visibility = View.GONE
        textContainer.visibility = View.VISIBLE

        // ✅ Point B: ByteArray buffering (mark/reset support for old formats)
        val stream = contentResolver.openInputStream(currentFileUri!!) ?: return
        val byteArray = stream.readBytes()
        var fileStreamToProcess: InputStream = ByteArrayInputStream(byteArray)

        if (filePassword != null) {
            val pfs = POIFSFileSystem(ByteArrayInputStream(byteArray))
            val info = EncryptionInfo(pfs)
            val decryptor = Decryptor.getInstance(info)
            if (decryptor.verifyPassword(filePassword)) {
                fileStreamToProcess = decryptor.getDataStream(pfs)
            } else {
                throw Exception("password validation failure")
            }
        }

        when (fileExtension) {
            "docx" -> {
                // ✅ Point C: Robust paragraph-based parsing
                val doc = XWPFDocument(fileStreamToProcess)
                val builder = StringBuilder()
                doc.paragraphs.forEach { paragraph ->
                    builder.append(paragraph.text).append("\n")
                }
                contentViewText.text = builder.toString()
                doc.close()
                calculateVirtualPagination()
            }
            "doc" -> {
                val doc = HWPFDocument(fileStreamToProcess)
                contentViewText.text = doc.text.toString()
                doc.close()
                calculateVirtualPagination()
            }
            "xlsx" -> {
                textContainer.visibility = View.GONE
                tableContainer.visibility = View.VISIBLE
                contentViewTable.removeAllViews()

                val workbook = XSSFWorkbook(fileStreamToProcess)
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter()   // ✅ Point D

                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val tableRow = TableRow(this)
                    for (c in 0 until row.lastCellNum) {
                        val cell = row.getCell(c) ?: continue
                        val tv = TextView(this)
                        tv.text = formatter.formatCellValue(cell)   // ✅ Point D
                        tv.setPadding(12, 8, 12, 8)
                        tableRow.addView(tv)
                    }
                    contentViewTable.addView(tableRow)
                }
                workbook.close()
                pageIndicator.text = "Spreadsheet Structure: Sheet Index 1"
                totalVirtualPages = 1
            }
            "xls" -> {
                textContainer.visibility = View.GONE
                tableContainer.visibility = View.VISIBLE
                contentViewTable.removeAllViews()

                val workbook = HSSFWorkbook(fileStreamToProcess)
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter()   // ✅ Point D

                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val tableRow = TableRow(this)
                    for (c in 0 until row.lastCellNum) {
                        val cell = row.getCell(c) ?: continue
                        val tv = TextView(this)
                        tv.text = formatter.formatCellValue(cell)   // ✅ Point D
                        tv.setPadding(12, 8, 12, 8)
                        tableRow.addView(tv)
                    }
                    contentViewTable.addView(tableRow)
                }
                workbook.close()
                pageIndicator.text = "Spreadsheet Structure: Legacy Sheet"
                totalVirtualPages = 1
            }
            "pptx" -> {
                val ppt = XMLSlideShow(fileStreamToProcess)
                val builder = StringBuilder()
                ppt.slides.forEachIndexed { index, slide ->
                    builder.append("--- [Slide ${index + 1}] ---\n")
                    slide.shapes.forEach { shape ->
                        if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                            builder.append(shape.text).append("\n")
                        }
                    }
                }
                contentViewText.text = builder.toString()
                totalVirtualPages = ppt.slides.size
                pageIndicator.text = "Page 1 of $totalVirtualPages"
                ppt.close()
            }
            "ppt" -> {
                val ppt = HSLFSlideShow(fileStreamToProcess)
                val builder = StringBuilder()
                ppt.slides.forEachIndexed { index, slide ->
                    builder.append("--- [Slide ${index + 1}] ---\n")
                    slide.shapes.forEach { shape ->
                        if (shape is org.apache.poi.hslf.usermodel.HSLFTextShape) {
                            builder.append(shape.text).append("\n")
                        }
                    }
                }
                contentViewText.text = builder.toString()
                totalVirtualPages = ppt.slides.size
                pageIndicator.text = "Page 1 of $totalVirtualPages"
                ppt.close()
            }
        }
    }

    private fun calculateVirtualPagination() {
        contentViewText.post {
            val totalLines = contentViewText.lineCount
            totalVirtualPages = if (totalLines <= 0) 1 else (totalLines / linesPerPageThreshold) + 1
            pageIndicator.text = "Page 1 of $totalVirtualPages"
        }
    }

    private fun performGoToPageJump(targetPage: Int) {
        if (targetPage < 1 || targetPage > totalVirtualPages) {
            Toast.makeText(this, "Target page out of local range bounds", Toast.LENGTH_SHORT).show()
            return
        }
        currentVirtualPage = targetPage

        if (fileExtension == "pptx" || fileExtension == "ppt") {
            val slideMarker = "--- [Slide $targetPage] ---"
            val textLayout = contentViewText.layout
            val textString = contentViewText.text.toString()
            val charIndex = textString.indexOf(slideMarker)
            if (charIndex != -1 && textLayout != null) {
                val lineIndex = textLayout.getLineForOffset(charIndex)
                val yCoordinate = textLayout.getLineTop(lineIndex)
                textContainer.scrollTo(0, yCoordinate)
            }
        } else {
            val textLayout = contentViewText.layout
            if (textLayout != null) {
                val targetLineIndex = min(textLayout.lineCount - 1, (targetPage - 1) * linesPerPageThreshold)
                val yCoordinate = textLayout.getLineTop(targetLineIndex)
                textContainer.scrollTo(0, yCoordinate)
            }
        }
        pageIndicator.text = "Page $currentVirtualPage of $totalVirtualPages"
    }

    private fun evaluateFilePropertiesMetadata() {
        val file = File(currentFileUri?.path ?: "")
        toolbar.subtitle = if (file.exists()) {
            "${file.name} (${file.length() / 1024} KB)"
        } else {
            "Local Asset Stream"
        }
    }

    private fun dispatchPrintJob() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "OpenDoc Print Job"

        val webView = android.webkit.WebView(this)
        val htmlContent = StringBuilder()
        htmlContent.append("<html><body><pre style='font-family: monospace; white-space: pre-wrap;'>")

        if (textContainer.visibility == View.VISIBLE) {
            htmlContent.append(
                contentViewText.text.toString()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
            )
        } else {
            htmlContent.append("Spreadsheet Grid Structure Document Payload")
        }
        htmlContent.append("</pre></body></html>")

        webView.loadDataWithBaseURL(null, htmlContent.toString(), "text/html", "utf-8", null)
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.opendoc_viewer_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val navigationActionItem = menu.findItem(R.id.action_opendoc_goto)
        if (fileExtension == "json" || fileExtension == "csv" ||
            fileExtension == "txt" || fileExtension == "xlsx" || fileExtension == "xls"
        ) {
            navigationActionItem?.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_opendoc_print -> {
                dispatchPrintJob()
                return true
            }
            R.id.action_opendoc_goto -> {
                displayGoToPageDialogBox()
                return true
            }
            R.id.action_opendoc_properties -> {
                displayFilePropertiesDialogBox()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun displayGoToPageDialogBox() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Jump to Page")
        builder.setMessage("Enter page number (Max: $totalVirtualPages):")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton("Go") { dialog, _ ->
            val pageNumberString = input.text.toString()
            if (pageNumberString.isNotEmpty()) {
                performGoToPageJump(pageNumberString.toInt())
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun displayFilePropertiesDialogBox() {
        val metaBuilder = AlertDialog.Builder(this)
        metaBuilder.setTitle("Document Properties")

        val infoMessage = "Format: .${fileExtension.uppercase()}\n" +
                "Engine: OpenDoc Multi-Format Offline Renderer\n" +
                "Encryption: ${if (filePassword != null) "Password Protected" else "None (Open File)"}\n" +
                "Security Context: 100% Isolated On-Device Mode"

        metaBuilder.setMessage(infoMessage)
        metaBuilder.setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
        metaBuilder.show()
    }
}
