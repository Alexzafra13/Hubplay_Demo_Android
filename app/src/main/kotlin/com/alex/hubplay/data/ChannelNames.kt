package com.alex.hubplay.data

/**
 * Sufijos técnicos que arrastran los nombres de canal de las listas M3U
 * y que no aportan nada en una TV: calidad entre paréntesis
 * ("(1080p)", "(HD)", "(FHD)") y etiquetas entre corchetes
 * ("[Geo-blocked]", "[Not 24/7]"). Se quitan solo para MOSTRAR; el
 * nombre crudo sigue en el servidor y en la búsqueda.
 */
private val QUALITY_SUFFIX = Regex("""\s*\((?:\d{3,4}[ip]|[SH]D|FHD|UHD|4K|8K)\)""", RegexOption.IGNORE_CASE)
private val BRACKET_TAG    = Regex("""\s*\[[^\]]*]""")
private val MULTI_SPACE    = Regex("""\s{2,}""")

fun cleanChannelName(raw: String): String {
    val cleaned = raw
        .replace(BRACKET_TAG, "")
        .replace(QUALITY_SUFFIX, "")
        .replace(MULTI_SPACE, " ")
        .trim()
    // Si el nombre era SOLO etiquetas, mejor el crudo que un string vacío.
    return cleaned.ifBlank { raw.trim() }
}
