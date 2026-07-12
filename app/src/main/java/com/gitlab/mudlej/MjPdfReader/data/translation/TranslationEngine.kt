// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class TranslationEngine(
    val id: String,
    @StringRes val titleRes: Int,
    val urlTemplate: String?,
    val langOverrides: Map<String, String> = emptyMap(),
) {
    GOOGLE(
        "google",
        R.string.translation_engine_google,
        "https://translate.google.com/?sl=auto&tl={lang}&text={text}&op=translate",
        mapOf("zh" to "zh-CN"),
    ),
    DEEPL(
        "deepl",
        R.string.translation_engine_deepl,
        "https://www.deepl.com/translator#auto/{lang}/{text}",
        mapOf("no" to "nb", "pt" to "pt-BR"),
    ),
    BING(
        "bing",
        R.string.translation_engine_bing,
        "https://www.bing.com/translator/?from=auto&to={lang}&text={text}",
        mapOf("zh" to "zh-Hans"),
    ),
    LINGVA(
        "lingva",
        R.string.translation_engine_lingva,
        "https://lingva.ml/auto/{lang}/{text}",
    ),
    LIBRE_TRANSLATE(
        "libretranslate",
        R.string.translation_engine_libretranslate,
        "https://libretranslate.com/?q={text}&source=auto&target={lang}",
    ),
    CUSTOM(
        "custom",
        R.string.translation_engine_custom,
        null,
    );

    companion object {
        fun fromId(id: String): TranslationEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

data class TranslationSettings(
    val mode: String,
    val engine: TranslationEngine,
    val customTemplate: String,
    val targetLanguage: String,
)
