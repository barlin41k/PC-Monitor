package com.bxrlya.pcmonitor

import android.content.res.Configuration
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.toColorInt
import android.content.Context

fun String.coloredSpan(start: Int, end: Int, context: Context): Spannable {
    val spannable = SpannableString(this)

    val isDarkTheme = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val color = if (isDarkTheme) "#f7f2f2" else "#050505"

    spannable.setSpan(
        ForegroundColorSpan(color.toColorInt()),
        start,
        end,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return spannable
}

fun getHourString(number: Int): String {
    val lastTwoDigits = number % 100
    val lastDigit = number % 10

    val word = when {
        lastTwoDigits in 11..14 -> "часов"
        lastDigit == 1 -> "час"
        lastDigit in 2..4 -> "часа"
        else -> "часов"
    }

    return "$number $word"
}
