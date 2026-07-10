package com.aibook.android.core.model

enum class ReaderContentsStyle {
    CLASSIC,
    GROUPED;

    companion object {
        fun fromStoredValue(value: String?): ReaderContentsStyle =
            entries.firstOrNull { it.name == value } ?: CLASSIC
    }
}
