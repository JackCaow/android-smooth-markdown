package com.jackcaow.smoothmarkdown.demo

import android.content.res.AssetManager
import org.json.JSONObject

enum class DemoLanguage(val code: String, val nativeName: String) {
    Chinese("zh", "中文"),
    English("en", "English"),
    Japanese("ja", "日本語"),
    Spanish("es", "Español"),
    French("fr", "Français"),
    Korean("ko", "한국어");

    companion object {
        fun fromCode(code: String?): DemoLanguage = entries.firstOrNull { it.code == code } ?: Chinese
    }
}

/** Flutter's example/lib/l10n/app_localizations.dart, synced by tools/sync_flutter_l10n.py. */
class DemoLocalizations private constructor(private val translations: JSONObject) {
    companion object {
        fun load(assets: AssetManager): DemoLocalizations {
            val json = assets.open("l10n/flutter-localizations.json").bufferedReader().use { it.readText() }
            return DemoLocalizations(JSONObject(json).getJSONObject("translations"))
        }
    }

    fun text(language: DemoLanguage, key: String): String =
        translations.getJSONObject(language.code).optString(key).ifEmpty {
            translations.getJSONObject(DemoLanguage.Chinese.code).optString(key, key)
        }

    fun theme(language: DemoLanguage, index: Int): String = text(language, themeKeys[index])

    fun example(language: DemoLanguage, example: DemoExample): String {
        val key = exampleKeys[example.id] ?: return example.title
        return nativeTitles[language.code]?.get(key) ?: text(language, key)
    }

    fun page(language: DemoLanguage, page: DemoPage): String = when (page.id) {
        "math" -> text(language, "demo_math")
        "stream" -> text(language, "demo_streaming")
        "footnote" -> text(language, "demo_footnote")
        else -> page.title
    }

    fun chrome(language: DemoLanguage, key: String): String =
        nativeTitles[language.code]?.get(key) ?: key

    fun exportStatus(language: DemoLanguage, count: Int): String = when (language) {
        DemoLanguage.Chinese -> "已导出 $count 个字符"
        DemoLanguage.English -> "Exported $count characters"
        DemoLanguage.Japanese -> "$count 文字をエクスポートしました"
        DemoLanguage.Spanish -> "$count caracteres exportados"
        DemoLanguage.French -> "$count caractères exportés"
        DemoLanguage.Korean -> "$count 자 내보냄"
    }
}

private val themeKeys = listOf(
    "theme_default_light", "theme_default_dark", "theme_github", "theme_github_dark",
    "theme_vscode", "theme_vscode_dark",
)

private val exampleKeys = mapOf(
    "basic-formatting" to "example_basic",
    "headers" to "example_headers",
    "lists" to "example_lists",
    "code-blocks" to "example_code",
    "quotes-rules" to "quotes_rules",
    "links-images" to "links_images",
    "enhanced-ui" to "example_enhanced",
    "theme-showcase" to "example_theme",
    "details-summary" to "details_summary",
    "complex-example" to "complex_example",
)

// These labels are hard-coded in Flutter's demo or are absent from its l10n table.
private val nativeTitles: Map<String, Map<String, String>> = mapOf(
    "zh" to mapOf("quotes_rules" to "引用与分隔线", "links_images" to "链接与图片",
        "details_summary" to "详情与摘要", "complex_example" to "复杂示例", "examples" to "示例",
        "edit" to "编辑", "source" to "源码", "source_title" to "Markdown 源码",
        "close" to "关闭"),
    "en" to mapOf("quotes_rules" to "Quotes & Rules", "links_images" to "Links & Images",
        "details_summary" to "Details & Summary", "complex_example" to "Complex Example",
        "examples" to "Examples", "edit" to "Edit", "source" to "Source",
        "source_title" to "Markdown Source", "close" to "Close"),
    "ja" to mapOf("quotes_rules" to "引用と区切り線", "links_images" to "リンクと画像",
        "details_summary" to "詳細と概要", "complex_example" to "複雑な例", "examples" to "サンプル",
        "edit" to "編集", "source" to "ソース", "source_title" to "Markdown ソース",
        "close" to "閉じる"),
    "es" to mapOf("quotes_rules" to "Citas y Separadores", "links_images" to "Enlaces e Imágenes",
        "details_summary" to "Detalles y Resumen", "complex_example" to "Ejemplo Complejo",
        "examples" to "Ejemplos", "edit" to "Editar", "source" to "Fuente",
        "source_title" to "Fuente Markdown", "close" to "Cerrar"),
    "fr" to mapOf("quotes_rules" to "Citations et Séparateurs", "links_images" to "Liens et Images",
        "details_summary" to "Détails et Résumé", "complex_example" to "Exemple Complexe",
        "examples" to "Exemples", "edit" to "Modifier", "source" to "Source",
        "source_title" to "Source Markdown", "close" to "Fermer"),
    "ko" to mapOf("quotes_rules" to "인용과 구분선", "links_images" to "링크와 이미지",
        "details_summary" to "세부 정보와 요약", "complex_example" to "복합 예제",
        "examples" to "예제", "edit" to "편집", "source" to "원본",
        "source_title" to "Markdown 원본", "close" to "닫기"),
)
