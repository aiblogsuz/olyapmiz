package com.example.lifedots.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Uses Android's date picker so month changes cannot produce an invalid, lenient date. */
@Composable
fun DatePickerDialog(initialDate: Long, onDateSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selectedCallback = rememberUpdatedState(onDateSelected)
    val dismissCallback = rememberUpdatedState(onDismiss)
    DisposableEffect(context, initialDate) {
        val date = Instant.ofEpochMilli(initialDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val dialog = android.app.DatePickerDialog(context, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day)
            selectedCallback.value(selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }, date.year, date.monthValue - 1, date.dayOfMonth)
        dialog.setOnDismissListener { dismissCallback.value() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}
