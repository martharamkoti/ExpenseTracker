package com.ramkotimartha.expensetracker.ai

import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.data.TransactionWithTag
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import kotlin.math.abs

data class PositionedTextElement(
    val text: String,
    val left: Int,
    val top: Int,
    val bottom: Int
) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

data class ExtractedReceiptData(
    val amount: Double?,
    val merchantName: String?,
    val date: LocalDate?,
    val suggestedTag: TagSuggestion?,
    val rawText: String
) {
    val hasExtractedFields: Boolean
        get() = amount != null || !merchantName.isNullOrBlank() || date != null
}

/**
 * On-device heuristic & spatial receipt parser tailored for physical thermal receipts
 * and digital payment/order invoices.
 */
object ReceiptParser {

    private val NOISE_IDENTIFIER_REGEX = Regex(
        "(?i)\\b(gstin|gst\\s*no|fssai|cin|pan|tin|sac|hsn|tel|telephone|phone|ph|mob|mobile|whatsapp|pin\\s*code|pincode|invoice\\s*no|inv\\s*no|bill\\s*no|receipt\\s*no|order\\s*id|order\\s*no|txn\\s*id|utr|rrn|appr\\s*code|auth\\s*code|terminal|cashier|table\\s*no|token\\s*no)\\b"
    )

    private val EXCLUDED_AMOUNT_LINE_REGEX = Regex(
        "(?i)\\b(change\\s*due|change\\s*given|change|cash\\s*tendered|tendered|you\\s*saved|total\\s*savings|savings|discount|promo|coupon|cashback|loyalty|points|cgst|sgst|igst|utgst|vat|cess|round\\s*off|rounding|tip|service\\s*charge|delivery\\s*fee|packaging|qty\\s*total|total\\s*qty|total\\s*items|items\\s*count)\\b"
    )

    private val SUBTOTAL_LINE_REGEX = Regex(
        "(?i)\\b(sub\\s*-?\\s*total|item\\s*total|food\\s*total|taxable\\s*value|taxable\\s*amount|gross\\s*amount)\\b"
    )

    private val STRONG_TOTAL_REGEX = Regex(
        "(?i)\\b(grand\\s*total|net\\s*payable|amount\\s*payable|total\\s*payable|bill\\s*total|order\\s*total|total\\s*amount|final\\s*amount|net\\s*amount|net\\s*total|amount\\s*paid|paid\\s*amount|total\\s*paid|total\\s*due|amount\\s*due|balance\\s*due|to\\s*pay|paid\\s*via)\\b"
    )

    private val STANDALONE_TOTAL_REGEX = Regex(
        "(?i)(?:^|\\s)total(?:\\s*\\([^)]*\\))?(?:\\s*:|\\s+|$)"
    )

    private val GENERIC_MERCHANT_HEADER_REGEX = Regex(
        "(?i)^(tax\\s*invoice|retail\\s*invoice|invoice|cash\\s*memo|bill\\s*of\\s*supply|estimate|receipt|payment\\s*receipt|e-?receipt|original|duplicate|customer\\s*copy|merchant\\s*copy|welcome|thank\\s*you.*|order\\s*summary|bill\\s*summary|transaction\\s*successful|payment\\s*successful|paid\\s*successfully)$"
    )

    private val ADDRESS_NOISE_REGEX = Regex(
        "(?i)\\b(road|rd|street|st|lane|nagar|layout|sector|phase|floor|flr|plot|shop\\s*no|door\\s*no|building|bldg|complex|opposite|opp|near|behind|dist|district|state|india|maharashtra|karnataka|tamil\\s*nadu|telangana|delhi|gujarat|kerala|bengaluru|bangalore|mumbai|hyderabad|chennai|pune|kolkata|gurgaon|gurugram|noida|www\\.|http|@)\\b|\\b\\d{6}\\b"
    )

    private val KNOWN_BRANDS = listOf(
        "Swiggy", "Zomato", "Blinkit", "Zepto", "BigBasket", "Instamart", "DMart",
        "Reliance Smart", "Reliance Fresh", "Reliance Digital", "Starbucks", "Blue Tokai",
        "Third Wave Coffee", "McDonald's", "Domino's", "Pizza Hut", "Burger King", "KFC",
        "Subway", "Haldiram's", "Apollo Pharmacy", "MedPlus", "PharmEasy", "Tata 1mg",
        "Uber", "Ola", "Rapido", "IndianOil", "Bharat Petroleum", "HP Petrol", "Shell",
        "Amazon", "Flipkart", "Myntra", "Ajio", "Nykaa", "Decathlon", "IKEA", "Croma",
        "Vijay Sales", "Zudio", "Westside", "Pantaloons", "Shoppers Stop", "BookMyShow",
        "PVR Cinemas", "INOX", "Airtel", "Jio", "Netflix", "Spotify"
    )

    private val MONTH_MAP = mapOf(
        "jan" to Month.JANUARY, "january" to Month.JANUARY,
        "feb" to Month.FEBRUARY, "february" to Month.FEBRUARY,
        "mar" to Month.MARCH, "march" to Month.MARCH,
        "apr" to Month.APRIL, "april" to Month.APRIL,
        "may" to Month.MAY,
        "jun" to Month.JUNE, "june" to Month.JUNE,
        "jul" to Month.JULY, "july" to Month.JULY,
        "aug" to Month.AUGUST, "august" to Month.AUGUST,
        "sep" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "september" to Month.SEPTEMBER,
        "oct" to Month.OCTOBER, "october" to Month.OCTOBER,
        "nov" to Month.NOVEMBER, "november" to Month.NOVEMBER,
        "dec" to Month.DECEMBER, "december" to Month.DECEMBER
    )

    /**
     * Reconstructs reading order by grouping spatially aligned OCR lines into horizontal rows
     * (so left-aligned labels like "Grand Total" stay paired with right-aligned amounts like "540.00"),
     * then extracts structured expense fields.
     */
    fun parseFromPositionedLines(
        elements: List<PositionedTextElement>,
        availableTags: List<Tag>,
        pastTransactions: List<TransactionWithTag> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): ExtractedReceiptData {
        if (elements.isEmpty()) {
            return ExtractedReceiptData(null, null, null, null, "")
        }

        val sortedByTop = elements
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<PositionedTextElement> { it.top }.thenBy { it.left })

        val rows = mutableListOf<MutableList<PositionedTextElement>>()
        for (elem in sortedByTop) {
            val matchingRow = rows.lastOrNull { row ->
                val avgCenterY = row.map { it.centerY }.average().toFloat()
                val avgHeight = row.map { it.height }.average().toFloat()
                val tolerance = maxOf(avgHeight, elem.height.toFloat()) * 0.62f
                abs(elem.centerY - avgCenterY) <= tolerance
            }
            if (matchingRow != null) {
                matchingRow.add(elem)
            } else {
                rows.add(mutableListOf(elem))
            }
        }

        val stitchedLines = rows.map { row ->
            row.sortedBy { it.left }.joinToString("   ") { it.text.trim() }
        }

        return parseFromLines(
            lines = stitchedLines,
            availableTags = availableTags,
            pastTransactions = pastTransactions,
            today = today
        )
    }

    fun parseFromLines(
        lines: List<String>,
        availableTags: List<Tag>,
        pastTransactions: List<TransactionWithTag> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): ExtractedReceiptData {
        val cleanedLines = lines
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }

        val rawText = cleanedLines.joinToString("\n")
        if (cleanedLines.isEmpty()) {
            return ExtractedReceiptData(null, null, null, null, "")
        }

        val amount = extractTotalAmount(cleanedLines)
        val date = extractDate(cleanedLines, today)
        val merchant = extractMerchantName(cleanedLines)
        val suggestedTag = suggestCategory(merchant, cleanedLines, availableTags, pastTransactions)

        return ExtractedReceiptData(
            amount = amount,
            merchantName = merchant,
            date = date,
            suggestedTag = suggestedTag,
            rawText = rawText
        )
    }

    internal fun extractTotalAmount(lines: List<String>): Double? {
        data class Candidate(val amount: Double, val priority: Int, val lineIndex: Int)

        val candidates = mutableListOf<Candidate>()

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)

            if (NOISE_IDENTIFIER_REGEX.containsMatchIn(lower) && !STRONG_TOTAL_REGEX.containsMatchIn(lower)) {
                continue
            }

            val isStrongTotal = STRONG_TOTAL_REGEX.containsMatchIn(lower)
            val isSubtotal = SUBTOTAL_LINE_REGEX.containsMatchIn(lower)
            val isExcluded = EXCLUDED_AMOUNT_LINE_REGEX.containsMatchIn(lower) && !isStrongTotal
            if (isExcluded) continue

            val isPlainTotal = !isStrongTotal && !isSubtotal && STANDALONE_TOTAL_REGEX.containsMatchIn(lower)

            val amountsOnLine = extractMonetaryValues(line, requireDecimalOrCurrency = !(isStrongTotal || isPlainTotal || isSubtotal))

            if (isStrongTotal) {
                val valueOnSameLine = amountsOnLine.lastOrNull()
                if (valueOnSameLine != null) {
                    candidates.add(Candidate(valueOnSameLine, priority = 100, lineIndex = i))
                } else if (i + 1 < lines.size) {
                    val nextLineValues = extractMonetaryValues(lines[i + 1], requireDecimalOrCurrency = false)
                    nextLineValues.lastOrNull()?.let {
                        candidates.add(Candidate(it, priority = 95, lineIndex = i + 1))
                    }
                }
            } else if (isPlainTotal) {
                val valueOnSameLine = amountsOnLine.lastOrNull()
                if (valueOnSameLine != null) {
                    candidates.add(Candidate(valueOnSameLine, priority = 85, lineIndex = i))
                } else if (i + 1 < lines.size) {
                    val nextLineValues = extractMonetaryValues(lines[i + 1], requireDecimalOrCurrency = false)
                    nextLineValues.lastOrNull()?.let {
                        candidates.add(Candidate(it, priority = 80, lineIndex = i + 1))
                    }
                }
            } else if (isSubtotal) {
                amountsOnLine.lastOrNull()?.let {
                    candidates.add(Candidate(it, priority = 60, lineIndex = i))
                }
            } else {
                // General monetary lines (prefer lower half of receipt and currency/decimal formatted amounts)
                val inLowerSection = i >= (lines.size * 0.35).toInt()
                val priority = if (inLowerSection) 30 else 15
                amountsOnLine.forEach { amt ->
                    candidates.add(Candidate(amt, priority = priority, lineIndex = i))
                }
            }
        }

        if (candidates.isEmpty()) return null

        val maxPriority = candidates.maxOf { it.priority }
        val topTier = candidates.filter { it.priority >= maxPriority - 5 }

        return if (maxPriority >= 80) {
            // Among explicit total lines, prefer the highest priority, then bottom-most total line
            topTier.maxWithOrNull(compareBy<Candidate> { it.priority }.thenBy { it.lineIndex })?.amount
        } else {
            // Among fallback lines without a "Total" label, pick the largest plausible monetary value
            topTier.maxByOrNull { it.amount }?.amount
        }
    }

    private val CURRENCY_OR_NUMBER_REGEX = Regex(
        "(?:(?:₹|\\$|€|£|(?i:rs\\.?|inr))\\s*)?(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+\\.\\d{1,2}|\\d{1,7})"
    )

    private val DATE_LIKE_TOKEN_REGEX = Regex(
        "\\b\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}\\b|\\b\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}\\b"
    )

    private val TIME_LIKE_TOKEN_REGEX = Regex(
        "\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?i:am|pm)?\\b"
    )

    private fun extractMonetaryValues(line: String, requireDecimalOrCurrency: Boolean): List<Double> {
        // Strip dates, times, and "2 x 150" quantity multipliers so they aren't misread as totals
        val sanitized = line
            .replace(DATE_LIKE_TOKEN_REGEX, " ")
            .replace(TIME_LIKE_TOKEN_REGEX, " ")
            .replace(Regex("\\b\\d+\\s*[xX*]\\s*(?=\\d)"), " ")
            .replace(Regex("\\b\\d+(?:\\.\\d+)?\\s*%"), " ")

        val results = mutableListOf<Double>()
        for (match in CURRENCY_OR_NUMBER_REGEX.findAll(sanitized)) {
            val fullMatch = match.value.trim()
            val rawNum = match.groupValues[1]
            val hasCurrencyPrefix = fullMatch.length > rawNum.length &&
                Regex("^(?:₹|\\$|€|£|(?i:rs\\.?|inr))").containsMatchIn(fullMatch)
            val hasDecimal = rawNum.contains('.')
            val hasCommaGrouping = rawNum.contains(',')

            if (requireDecimalOrCurrency && !hasCurrencyPrefix && !hasDecimal && !hasCommaGrouping) {
                continue
            }

            // Skip bare 4-digit years like 2024..2030 when no decimal or currency prefix is present
            val normalizedNum = rawNum.replace(",", "")
            val parsed = normalizedNum.toDoubleOrNull() ?: continue
            if (!hasDecimal && !hasCurrencyPrefix && parsed in 1990.0..2035.0) {
                continue
            }

            if (parsed > 0.0 && parsed <= 5_000_000.0) {
                results.add(parsed)
            }
        }
        return results
    }

    internal fun extractDate(lines: List<String>, today: LocalDate = LocalDate.now()): LocalDate? {
        data class DateCandidate(val date: LocalDate, val hasDateKeyword: Boolean, val lineIndex: Int)

        val candidates = mutableListOf<DateCandidate>()

        // 1. Numeric dd/MM/yyyy, dd-MM-yyyy, dd.MM.yy
        val dmyRegex = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})\\b")
        // 2. Numeric ISO yyyy-MM-dd or yyyy/MM/dd
        val ymdRegex = Regex("\\b(20\\d{2})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})\\b")
        // 3. Day Month Year: 27 Nov 2026, 27-Nov-26, 27th November 2026
        val dMonYRegex = Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s,\\-/]+([a-z]{3,9})[.,]?[\\s,\\-/]+(\\d{2,4})\\b")
        // 4. Month Day Year: Nov 27, 2026
        val monDYRegex = Regex("(?i)\\b([a-z]{3,9})[.,]?\\s+(\\d{1,2})(?:st|nd|rd|th)?[\\s,\\-]+(\\d{2,4})\\b")

        for ((index, line) in lines.withIndex()) {
            val hasKeyword = Regex("(?i)\\b(date|dt|dated|time|ordered\\s*on|paid\\s*on)\\b").containsMatchIn(line)

            for (m in ymdRegex.findAll(line)) {
                val year = m.groupValues[1].toIntOrNull() ?: continue
                val month = m.groupValues[2].toIntOrNull() ?: continue
                val day = m.groupValues[3].toIntOrNull() ?: continue
                buildValidDate(year, month, day, today)?.let {
                    candidates.add(DateCandidate(it, hasKeyword, index))
                }
            }

            for (m in dMonYRegex.findAll(line)) {
                val day = m.groupValues[1].toIntOrNull() ?: continue
                val month = MONTH_MAP[m.groupValues[2].lowercase(Locale.ROOT)]?.value ?: continue
                val year = normalizeYear(m.groupValues[3].toIntOrNull() ?: continue)
                buildValidDate(year, month, day, today)?.let {
                    candidates.add(DateCandidate(it, hasKeyword, index))
                }
            }

            for (m in monDYRegex.findAll(line)) {
                val month = MONTH_MAP[m.groupValues[1].lowercase(Locale.ROOT)]?.value ?: continue
                val day = m.groupValues[2].toIntOrNull() ?: continue
                val year = normalizeYear(m.groupValues[3].toIntOrNull() ?: continue)
                buildValidDate(year, month, day, today)?.let {
                    candidates.add(DateCandidate(it, hasKeyword, index))
                }
            }

            for (m in dmyRegex.findAll(line)) {
                val first = m.groupValues[1].toIntOrNull() ?: continue
                val second = m.groupValues[2].toIntOrNull() ?: continue
                val rawYear = m.groupValues[3].toIntOrNull() ?: continue
                if (first > 31) continue // Already handled by ymdRegex
                val year = normalizeYear(rawYear)

                // Prefer DD/MM/YYYY (standard in India), fallback to MM/DD/YYYY if second > 12
                val date = buildValidDate(year, second, first, today)
                    ?: buildValidDate(year, first, second, today)
                if (date != null) {
                    candidates.add(DateCandidate(date, hasKeyword, index))
                }
            }
        }

        return candidates
            .sortedWith(compareByDescending<DateCandidate> { it.hasDateKeyword }.thenBy { it.lineIndex })
            .firstOrNull()
            ?.date
    }

    private fun normalizeYear(rawYear: Int): Int {
        return if (rawYear in 0..99) 2000 + rawYear else rawYear
    }

    private fun buildValidDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (year !in 2000..(today.year + 1) || month !in 1..12 || day !in 1..31) return null
        return try {
            val candidate = LocalDate.of(year, month, day)
            if (!candidate.isAfter(today)) candidate else null
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractMerchantName(lines: List<String>): String? {
        // First check if a well-known brand appears in the top portion of the receipt
        val headerWindow = lines.take(10)
        val joinedHeader = headerWindow.joinToString(" ")
        for (brand in KNOWN_BRANDS) {
            if (Regex("(?i)\\b${Regex.escape(brand)}\\b").containsMatchIn(joinedHeader)) {
                return brand
            }
        }

        // Check for explicit "Paid to <Merchant>" / "Merchant: <Name>" patterns (common on UPI/digital receipts)
        val paidToRegex = Regex("(?i)^(?:paid\\s+to|merchant(?:\\s*name)?|store(?:\\s*name)?|seller|biller)\\s*:?\\s*(.+)$")
        for (line in lines.take(12)) {
            val match = paidToRegex.find(line)
            if (match != null) {
                val extracted = cleanMerchantCandidate(match.groupValues[1])
                if (extracted != null) return extracted
            }
        }

        // Otherwise inspect the top header lines for the store/restaurant title
        for (line in headerWindow) {
            val strippedWelcome = line.replace(Regex("(?i)^welcome\\s+to\\s+"), "").trim()
            val candidate = cleanMerchantCandidate(strippedWelcome) ?: continue
            return candidate
        }

        return null
    }

    private fun cleanMerchantCandidate(raw: String): String? {
        // Take first column if multiple columns were stitched with wide spacing
        val primarySegment = raw.split(Regex("\\s{3,}")).firstOrNull()?.trim() ?: raw.trim()
        val cleaned = primarySegment
            .replace(Regex("^[#*\\-=~_]+|[#*\\-=~_]+$"), "")
            .trim()

        if (cleaned.length !in 3..45) return null
        if (GENERIC_MERCHANT_HEADER_REGEX.matches(cleaned)) return null
        if (NOISE_IDENTIFIER_REGEX.containsMatchIn(cleaned)) return null
        if (ADDRESS_NOISE_REGEX.containsMatchIn(cleaned)) return null
        if (DATE_LIKE_TOKEN_REGEX.containsMatchIn(cleaned) || TIME_LIKE_TOKEN_REGEX.containsMatchIn(cleaned)) return null
        if (STRONG_TOTAL_REGEX.containsMatchIn(cleaned) || STANDALONE_TOTAL_REGEX.containsMatchIn(cleaned)) return null

        val letterCount = cleaned.count { it.isLetter() }
        val digitCount = cleaned.count { it.isDigit() }
        if (letterCount < 3 || digitCount > letterCount) return null

        // Convert shouting ALL-CAPS store titles to clean Title Case while preserving short acronyms
        return if (cleaned == cleaned.uppercase(Locale.ROOT) && cleaned.length > 4) {
            cleaned.lowercase(Locale.ROOT).split(" ").joinToString(" ") { word ->
                if (word.length <= 2 && word.all { it.isLetter() } && word in setOf("hp", "io", "ac", "uk", "us")) {
                    word.uppercase(Locale.ROOT)
                } else {
                    word.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }
                }
            }
        } else {
            cleaned
        }
    }

    private fun suggestCategory(
        merchantName: String?,
        lines: List<String>,
        availableTags: List<Tag>,
        pastTransactions: List<TransactionWithTag>
    ): TagSuggestion? {
        if (!merchantName.isNullOrBlank()) {
            val fromMerchant = SmartTagSuggester.suggestTag(
                note = merchantName,
                type = TransactionType.EXPENSE,
                availableTags = availableTags,
                pastTransactions = pastTransactions
            )
            if (fromMerchant != null) return fromMerchant
        }

        // Second pass: inspect item lines from the receipt body (e.g., "milk bread eggs" -> Groceries)
        val bodySnippet = lines
            .filterNot { NOISE_IDENTIFIER_REGEX.containsMatchIn(it) || ADDRESS_NOISE_REGEX.containsMatchIn(it) }
            .take(25)
            .joinToString(" ")

        return SmartTagSuggester.suggestTag(
            note = bodySnippet,
            type = TransactionType.EXPENSE,
            availableTags = availableTags,
            pastTransactions = pastTransactions
        )
    }
}
