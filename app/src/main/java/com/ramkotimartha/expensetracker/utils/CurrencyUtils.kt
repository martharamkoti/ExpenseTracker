package com.ramkotimartha.expensetracker.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyUtils {
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    
    // Indian Numbering System: #,##,##,###.##
    private val formatter = DecimalFormat("#,##,###.##", symbols)
    
    // Raw formatter for input fields (no commas, no trailing .0)
    private val rawFormatter = DecimalFormat("0.##", symbols)

    fun formatAmount(amount: Double): String {
        return formatter.format(amount)
    }
    
    fun formatRawAmount(amount: Double): String {
        return rawFormatter.format(amount)
    }
    
    fun formatCurrency(amount: Double): String {
        return "₹${formatAmount(amount)}"
    }
}
