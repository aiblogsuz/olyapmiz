package com.example.lifedots.ui.components

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BirthdayDraftTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test fun `invalid month length never rolls into next month`() {
        assertNull(BirthdayDraft("31", "02", "2024").resolve(today))
        assertEquals(LocalDate.of(2024, 2, 29), BirthdayDraft("29", "02", "2024").resolve(today))
        assertNull(BirthdayDraft("29", "02", "2023").resolve(today))
    }
    @Test fun `partial year and future birthday cannot be saved`() {
        assertNull(BirthdayDraft("1", "1", "202").resolve(today))
        assertNull(BirthdayDraft("26", "10", "2026").resolve(today))
        assertEquals(today, BirthdayDraft("25", "10", "2026").resolve(today))
    }
    @Test fun `optional empty birthday differs from incomplete input`() {
        assertTrue(BirthdayDraft("", "", "").isBlank)
        assertFalse(BirthdayDraft("1", "", "").isBlank)
        assertNull(BirthdayDraft("1", "", "").resolve(today))
    }
}
