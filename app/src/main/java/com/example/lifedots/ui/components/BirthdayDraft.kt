package com.example.lifedots.ui.components

import java.time.LocalDate

/** Blank is optional; a partly entered or impossible birthday is never saved. */
internal data class BirthdayDraft(val day: String, val month: String, val year: String) {
    val isBlank: Boolean get() = day.isBlank() && month.isBlank() && year.isBlank()
    fun resolve(today: LocalDate): LocalDate? {
        if (year.length != 4) return null
        val d = day.toIntOrNull() ?: return null
        val m = month.toIntOrNull() ?: return null
        val y = year.toIntOrNull() ?: return null
        if (y !in 1900..today.year) return null
        return runCatching { LocalDate.of(y, m, d) }.getOrNull()?.takeUnless { it.isAfter(today) }
    }
}
