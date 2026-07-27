package com.aibook.android.feature.shelf

import android.content.Context
import android.content.SharedPreferences

object ShelfPreferences {
    const val FILE_NAME = "shelf_prefs"
    const val KEY_SHOW_CONTINUE_READING_CARDS = "show_continue_reading_cards"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun showContinueReadingCards(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(KEY_SHOW_CONTINUE_READING_CARDS, true)

    fun setShowContinueReadingCards(preferences: SharedPreferences, show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_CONTINUE_READING_CARDS, show).apply()
    }
}
