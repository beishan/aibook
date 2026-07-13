package com.aibook.android.feature.reader

object TxtParagraphNormalizer {

    fun mergeHardWrappedLines(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()
        lines.forEach { line ->
            val previous = merged.lastOrNull()
            val canMerge = previous != null && previous.isNotBlank() && line.isNotBlank() &&
                previous.length < 48 && line.length < 48 &&
                !previous.matches(Regex("^(第.+章|[\\-•*] .+|\\s{4,}.+)$")) &&
                !line.matches(Regex("^(第.+章|[\\-•*] .+|\\s{4,}.+)$"))
            if (canMerge) merged[merged.lastIndex] = "$previous$line" else merged += line
        }
        return merged
    }

    fun normalize(text: String, compressBlankLines: Boolean): List<String> {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.toList()
        if (!compressBlankLines) return lines

        return buildList {
            var previousWasBlank = false
            lines.forEach { line ->
                val isBlank = line.replace('\u3000', ' ').replace('\u00A0', ' ').isBlank()
                if (!isBlank || !previousWasBlank) add(if (isBlank) "" else line)
                previousWasBlank = isBlank
            }
        }
    }
}
