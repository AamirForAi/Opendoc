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
import android.webkit.PrintDocumentAdapter
importimport android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.opencsv.CSVReader
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

class OpenDocViewerActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var textContainer: View
    private lateinit var tableContainer: View
    private lateinit var contentViewText: TextView
    private lateinit var contentViewTable: TableLayout
    private lateinit var pageIndicator: TextView
    private lateinit var zoomIndicator: TextView

    private var currentFileUri: Uri? = null
    private var fileExtension: String = ""
    private var filePassword: String? = null

    // Zoom configurations (Limit set parameters)
    private var scaleFactor = 1.0f
    private val maxZoomLimit = 3.0f // 300% Maximum zoom constraint
    private val minZoomLimit = 0.8f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Page handling metrics
    private var totalVirtualPages = 1
    private var currentVirtualPage = 1
    private var lineBufferList = ArrayList<String>()
    private val linesPerPageThreshold = 45

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opendoc_viewer)

        // Initialize UI component nodes
        toolbar = findViewById(R.id.opendoc_toolbar)
        textContainer = findViewById(R.id.text_container_view)
        tableContainer = findViewById(R.id.table_container_view)
        contentViewText = findViewById(R.id.document_content_text)
        contentViewTable = findViewById(R.id.document_content_table)
        pageIndicator = findViewById(R.id.txt_page_indicator)
        zoomIndicator = findViewById(R.id.txt_zoom_indicator)

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

        // Initialize standalone isolated zoom layer gesture
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(minZoomLimit, min(scaleFactor, maxZoomLimit))
                
                // Adjust text size or dynamic layout spacing safely based on layout selection
                if (textContainer.visibility == View.VISIBLE) {
                    contentViewText.textSize = 16f * scaleFactor
                }
                
                val zoomPercentage = (scaleFactor * 100).toInt()
                zoomIndicator.text = "Zoom: $zoomPercentage%"
                return true
            }
        })

        // Attach gesture hook to layout base window elements
        textContainer.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
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
            // Check if failure is triggered due to core cryptographic security block
            if (e.message?.contains("password", ignoreCase = true) == true || 
                e is org.apache.poi.encryptedproperties.EncryptedPropertyException) {
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
            interceptAndLoadFile() // Re-attempt secure pipe execution
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
        lineBufferList.clear()

        reader.useLines { lines ->
            lines.forEach { line ->
                lineBufferList.add(line)
                builder.append(line).append("\n")
            }
        }
        contentViewText.text = builder.toString()
        
        // Plain text contains unified structural space - Lock pagination controls
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
            contentViewText.text = rawText // Fallback to raw payload display if corrupted
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
        
        while (csvReader.readNext().also { nextLine = it } != null) {
            val row = TableRow(this)
            nextLine?.forEach { cellText ->
                val tv = TextView(this)
                tv.text = cellText
                tv.setPadding(12, 8, 12, 8)
                if (isHeader) {
                    tv.setTypeface(null, Typeface.BOLD)
                    tv.setBackgroundColor(Color.LTGRAY)
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT)
                }
                row.addView(tv)
            }
            isHeader = false
            contentViewTable.addView(row)
        }
        pageIndicator.text = "Data Structure: Grid Matrix"
        totalVirtualPages = 1
    }

    private fun executeOfficeEngineParser() {
        tableContainer.visibility = View.GONE
