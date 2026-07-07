package com.gitlab.mudlej.MjPdfReader.data.annotation

import android.graphics.RectF
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed class AnnotationEdit {

    abstract val page: Int

    data class Add(
        override val page: Int,
        val group: String,
        val rects: List<RectF>,
        val color: Int,
        val contents: String,
    ) : AnnotationEdit()

    data class Recolor(
        override val page: Int,
        val group: String,
        val color: Int,
    ) : AnnotationEdit()

    data class Delete(
        override val page: Int,
        val group: String,
    ) : AnnotationEdit()

    data class SetFieldText(
        override val page: Int,
        val fieldIndex: Int,
        val fieldName: String,
        val text: String,
    ) : AnnotationEdit()

    data class SetFieldChecked(
        override val page: Int,
        val fieldIndex: Int,
        val fieldName: String,
        val checked: Boolean,
    ) : AnnotationEdit()

    fun toJsonLine(): String {
        val json = JsonObject()
        json.addProperty(KEY_OP, opName())
        json.addProperty(KEY_PAGE, page)
        when (this) {
            is Add -> {
                json.addProperty(KEY_GROUP, group)
                json.addProperty(KEY_COLOR, color)
                json.addProperty(KEY_CONTENTS, contents)
                json.add(KEY_RECTS, rectsToJson(rects))
            }
            is Recolor -> {
                json.addProperty(KEY_GROUP, group)
                json.addProperty(KEY_COLOR, color)
            }
            is Delete -> json.addProperty(KEY_GROUP, group)
            is SetFieldText -> {
                json.addProperty(KEY_FIELD_INDEX, fieldIndex)
                json.addProperty(KEY_FIELD_NAME, fieldName)
                json.addProperty(KEY_TEXT, text)
            }
            is SetFieldChecked -> {
                json.addProperty(KEY_FIELD_INDEX, fieldIndex)
                json.addProperty(KEY_FIELD_NAME, fieldName)
                json.addProperty(KEY_CHECKED, checked)
            }
        }
        return json.toString()
    }

    private fun opName(): String = when (this) {
        is Add -> OP_ADD
        is Recolor -> OP_RECOLOR
        is Delete -> OP_DELETE
        is SetFieldText -> OP_SET_FIELD_TEXT
        is SetFieldChecked -> OP_SET_FIELD_CHECKED
    }

    companion object {
        private const val KEY_OP = "op"
        private const val KEY_PAGE = "page"
        private const val KEY_GROUP = "group"
        private const val KEY_COLOR = "color"
        private const val KEY_CONTENTS = "contents"
        private const val KEY_RECTS = "rects"
        private const val KEY_FIELD_INDEX = "fieldIndex"
        private const val KEY_FIELD_NAME = "fieldName"
        private const val KEY_TEXT = "text"
        private const val KEY_CHECKED = "checked"
        private const val OP_ADD = "add"
        private const val OP_RECOLOR = "recolor"
        private const val OP_DELETE = "delete"
        private const val OP_SET_FIELD_TEXT = "setFieldText"
        private const val OP_SET_FIELD_CHECKED = "setFieldChecked"

        fun fromJsonLine(line: String): AnnotationEdit? = runCatching {
            val json = JsonParser.parseString(line).asJsonObject
            val page = json.get(KEY_PAGE).asInt
            when (json.get(KEY_OP).asString) {
                OP_ADD -> Add(
                    page = page,
                    group = json.get(KEY_GROUP).asString,
                    rects = rectsFromJson(json.getAsJsonArray(KEY_RECTS)),
                    color = json.get(KEY_COLOR).asInt,
                    contents = json.get(KEY_CONTENTS).asString,
                )
                OP_RECOLOR -> Recolor(
                    page = page,
                    group = json.get(KEY_GROUP).asString,
                    color = json.get(KEY_COLOR).asInt,
                )
                OP_DELETE -> Delete(page = page, group = json.get(KEY_GROUP).asString)
                OP_SET_FIELD_TEXT -> SetFieldText(
                    page = page,
                    fieldIndex = json.get(KEY_FIELD_INDEX).asInt,
                    fieldName = json.get(KEY_FIELD_NAME).asString,
                    text = json.get(KEY_TEXT).asString,
                )
                OP_SET_FIELD_CHECKED -> SetFieldChecked(
                    page = page,
                    fieldIndex = json.get(KEY_FIELD_INDEX).asInt,
                    fieldName = json.get(KEY_FIELD_NAME).asString,
                    checked = json.get(KEY_CHECKED).asBoolean,
                )
                else -> null
            }
        }.getOrNull()

        private fun rectsToJson(rects: List<RectF>): JsonArray {
            val array = JsonArray()
            for (rect in rects) {
                val values = JsonArray()
                values.add(rect.left)
                values.add(rect.top)
                values.add(rect.right)
                values.add(rect.bottom)
                array.add(values)
            }
            return array
        }

        private fun rectsFromJson(array: JsonArray): List<RectF> {
            return array.map { element ->
                val values = element.asJsonArray
                RectF(
                    values.get(0).asFloat,
                    values.get(1).asFloat,
                    values.get(2).asFloat,
                    values.get(3).asFloat,
                )
            }
        }
    }
}
