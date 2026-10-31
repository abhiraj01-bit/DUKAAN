package com.dukaan.app.feature.invoice

import android.util.Log
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.InvoiceItem
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

private const val TAG = "InvoiceParser"

// ─── Extended parsed data with full GST fields ────────────────────────────

data class ParsedInvoiceData(
    val supplierName: String = "",
    val supplierAddress: String = "",
    val supplierGstin: String = "",
    val supplierPhone: String = "",
    val invoiceNumber: String = "",
    val invoiceDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val items: List<InvoiceItem> = emptyList(),
    val subtotalPaise: Long = 0L,
    val discountPaise: Long = 0L,
    val taxPaise: Long = 0L,
    val grandTotalPaise: Long = 0L,
    val rawText: String = "",
    val validationWarnings: List<String> = emptyList(),
    val extractionNotes: List<String> = emptyList()
)

// ─── Financial validation result ──────────────────────────────────────────

data class InvoiceValidationResult(
    val isValid: Boolean,
    val warnings: List<String>,
    /** true when totals mismatch by more than the allowed rounding tolerance */
    val hasCriticalMismatch: Boolean
)

// ─── Parser ───────────────────────────────────────────────────────────────

object InvoiceParser {

    // Max rounding tolerance in paise (₹2 = 200 paise)
    private const val ROUNDING_TOLERANCE_PAISE = 200L

    // ─── Regex patterns ──────────────────────────────────────────────────

    private val invoiceNumPatterns = listOf(
        Pattern.compile("""\b(?:invoice|inv|bill)\s*(?:no|num|number|#)?\s*[:#.\-]\s*([a-zA-Z0-9\-_/]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(?:invoice|inv|bill)\s+(?:no|num|number|#)\s*[:#.\-]?\s*([a-zA-Z0-9\-_/]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(?:b\.?no|bill\s*no)\s*[:#.\-]?\s*([a-zA-Z0-9\-_/]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b(?:voucher|vou|ref)[\s.:#-]*(?:no|num)?[\s.:#-]*([a-zA-Z0-9\-_/]+)""", Pattern.CASE_INSENSITIVE)
    )

    private val gstinPattern = Pattern.compile(
        """[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}""",
        Pattern.CASE_INSENSITIVE
    )

    private val phonePattern = Pattern.compile(
        """(?:\+91[\s-]?)?[6-9]\d{9}"""
    )

    private val totalPatterns = listOf(
        // Grand total – highest priority
        Pattern.compile("""(?:grand\s*total|net\s*payable|total\s*payable|amount\s*payable|final\s*total|net\s*amount)[.\s:]*[\u20B9Rs.]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // Total
        Pattern.compile("""(?:^|\s)total[.\s:]*[\u20B9Rs.]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // Rupee symbol followed by number (fallback)
        Pattern.compile("""[\u20B9][\s]*([0-9,]+(?:\.[0-9]{1,2})?)""")
    )

    private val subtotalPatterns = listOf(
        Pattern.compile("""(?:subtotal|sub\s*total|taxable\s*value|taxable\s*amount)[.\s:]*[\u20B9Rs.]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val taxPatterns = listOf(
        Pattern.compile("""(?:total\s*gst|gst\s*amount|total\s*tax|tax\s*amount|cgst\s*\+\s*sgst)[.\s:]*[\u20B9Rs.]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:cgst|sgst|igst)[.\s:]*(?:[0-9]+%)?[.\s]*[\u20B9Rs.]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val discountPatterns = listOf(
        Pattern.compile("""(?:discount|disc|less)[.\s:]*[\u20B9Rs.]?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("d/M/yyyy", Locale.getDefault()),
        SimpleDateFormat("d-M-yyyy", Locale.getDefault()),
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    ).onEach { it.isLenient = false }

    private val dateRegex = Pattern.compile(
        """(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})"""
    )

    // Line-item pattern: [Name] [Qty] [Unit?] [Price] [Total]
    private val lineItemPattern = Pattern.compile(
        """^(.{2,40}?)\s+(\d+(?:\.\d+)?)\s*(?:pcs?|kg|gm|ltr?|ml|box|pkt|bag|nos?|mtr?|yds?|doz)?\s+([0-9,]+(?:\.[0-9]{1,2})?)\s+([0-9,]+(?:\.[0-9]{1,2})?)$""",
        Pattern.CASE_INSENSITIVE
    )

    // ─── Main parse function ─────────────────────────────────────────────

    fun parse(ocrText: String, invoiceId: String = UuidGenerator.generate()): ParsedInvoiceData {
        if (ocrText.isBlank()) {
            return ParsedInvoiceData(rawText = ocrText, extractionNotes = listOf("OCR returned empty text"))
        }

        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()

        // 1. Supplier name (first 1–4 lines, skipping label-only lines)
        val supplierName = extractSupplierName(lines)
        if (supplierName.isEmpty()) notes.add("Supplier name could not be detected")

        // 2. Supplier GSTIN
        val gstin = extractGstin(ocrText)

        // 3. Supplier phone
        val phone = extractPhone(ocrText)

        // 4. Invoice number
        val invoiceNum = extractInvoiceNumber(lines)
        if (invoiceNum.isEmpty()) notes.add("Invoice number could not be detected")

        // 5. Dates
        val dates = extractAllDates(lines)
        val invoiceDate = dates.firstOrNull() ?: System.currentTimeMillis()
        val dueDate = if (dates.size > 1) dates[1] else null

        // 6. Monetary totals
        val grandTotal = extractAmount(ocrText, totalPatterns)
        val subtotal = extractAmount(ocrText, subtotalPatterns)
        val tax = extractAmount(ocrText, taxPatterns)
        val discount = extractAmount(ocrText, discountPatterns)

        if (grandTotal == 0L) notes.add("Grand total could not be detected")

        // 7. Line items
        val items = extractLineItems(lines, invoiceId)
        if (items.isEmpty()) notes.add("No product line items detected")

        // 8. Validate financials
        val effectiveSubtotal = if (subtotal > 0) subtotal else items.sumOf { it.lineTotalPaise }
        val computedTotal = effectiveSubtotal - discount + tax

        if (grandTotal > 0 && computedTotal > 0) {
            val diff = kotlin.math.abs(grandTotal - computedTotal)
            if (diff > ROUNDING_TOLERANCE_PAISE) {
                warnings.add("Total mismatch: extracted ₹${grandTotal / 100.0}, computed ₹${computedTotal / 100.0} from line items")
            }
        }

        if (items.any { it.lineTotalPaise == 0L }) {
            warnings.add("One or more line items have zero total – verify product rows")
        }

        Log.d(TAG, "Parsed: supplier='$supplierName', inv='$invoiceNum', total=${grandTotal}, items=${items.size}, warnings=${warnings.size}")

        return ParsedInvoiceData(
            supplierName = supplierName,
            supplierGstin = gstin,
            supplierPhone = phone,
            invoiceNumber = invoiceNum,
            invoiceDate = invoiceDate,
            dueDate = dueDate,
            items = items,
            subtotalPaise = if (subtotal > 0) subtotal else effectiveSubtotal,
            discountPaise = discount,
            taxPaise = tax,
            grandTotalPaise = if (grandTotal > 0) grandTotal else computedTotal,
            rawText = ocrText,
            validationWarnings = warnings,
            extractionNotes = notes
        )
    }

    // ─── Field extractors ────────────────────────────────────────────────

    private fun extractSupplierName(lines: List<String>): String {
        val skipWords = setOf(
            "invoice", "tax", "gst", "bill", "receipt", "purchase",
            "order", "date", "no", "number", "page", "original"
        )
        for (i in 0 until minOf(5, lines.size)) {
            val line = lines[i]
            val lower = line.lowercase()
            val hasSkipWord = skipWords.any { lower.contains(it) }
            val hasGstin = gstinPattern.matcher(line).find()
            if (!hasSkipWord && !hasGstin && line.length >= 3 && line.length <= 80) {
                return line
            }
        }
        return ""
    }

    private fun extractGstin(text: String): String {
        val m = gstinPattern.matcher(text)
        return if (m.find()) m.group(0)?.uppercase() ?: "" else ""
    }

    private fun extractPhone(text: String): String {
        val m = phonePattern.matcher(text)
        return if (m.find()) m.group(0)?.replace(Regex("[\\s-]"), "") ?: "" else ""
    }

    private fun extractInvoiceNumber(lines: List<String>): String {
        for (line in lines) {
            for (p in invoiceNumPatterns) {
                val m = p.matcher(line)
                if (m.find()) {
                    val num = m.group(1)?.trim() ?: ""
                    if (num.length >= 2 && num.length <= 30 && num.any { it.isLetterOrDigit() }) {
                        return num
                    }
                }
            }
        }
        return ""
    }

    private fun extractAllDates(lines: List<String>): List<Long> {
        val found = mutableListOf<Long>()
        for (line in lines) {
            val m = dateRegex.matcher(line)
            while (m.find()) {
                val dateStr = m.group(1) ?: continue
                val parsed = tryParseDate(dateStr)
                if (parsed != null && parsed > 0L) {
                    found.add(parsed)
                }
            }
        }
        return found.distinct()
    }

    private fun tryParseDate(dateStr: String): Long? {
        for (fmt in dateFormats) {
            try {
                val date = fmt.parse(dateStr)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    val year = cal.get(Calendar.YEAR)
                    if (year in 2000..2030) return date.time
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractAmount(text: String, patterns: List<Pattern>): Long {
        var best = 0L
        for (pattern in patterns) {
            val m = pattern.matcher(text)
            while (m.find()) {
                val amtStr = m.group(1) ?: continue
                val paise = CurrencyFormatter.rupeesToPaise(amtStr)
                if (paise > best) best = paise
            }
        }
        return best
    }

    private fun extractLineItems(lines: List<String>, invoiceId: String): List<InvoiceItem> {
        val items = mutableListOf<InvoiceItem>()

        // Find the table section (look for header keywords)
        val tableStartIdx = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            lower.contains("item") || lower.contains("product") || lower.contains("description") ||
            lower.contains("qty") || lower.contains("quantity")
        }

        val startIdx = if (tableStartIdx >= 0) tableStartIdx + 1 else 0

        for (i in startIdx until lines.size) {
            val line = lines[i]
            val m = lineItemPattern.matcher(line)
            if (m.find()) {
                val name = m.group(1)?.trim() ?: ""
                val qty = m.group(2)?.replace(",", "")?.toDoubleOrNull() ?: 1.0
                val price = CurrencyFormatter.rupeesToPaise(m.group(3)?.replace(",", "") ?: "0")
                val lineTotal = CurrencyFormatter.rupeesToPaise(m.group(4)?.replace(",", "") ?: "0")

                if (name.isNotBlank() && name.length > 2
                    && !name.any { it.isDigit() && name.count { c -> c.isDigit() } > name.length / 2 }) {

                    // Validate: line total should be approximately qty * price
                    val computedTotal = (qty * price).toLong()
                    val diff = kotlin.math.abs(lineTotal - computedTotal)
                    val isConsistent = lineTotal == 0L || diff <= ROUNDING_TOLERANCE_PAISE

                    items.add(
                        InvoiceItem(
                            id = UuidGenerator.generate(),
                            invoiceId = invoiceId,
                            productName = name,
                            quantity = qty,
                            purchasePricePaise = price,
                            lineTotalPaise = if (lineTotal > 0) lineTotal else computedTotal
                        )
                    )

                    if (!isConsistent) {
                        Log.w(TAG, "Line item '$name': qty*price=${computedTotal}, lineTotal=${lineTotal} (diff=${diff} paise)")
                    }
                }
            }
        }
        return items
    }

    // ─── Validation ──────────────────────────────────────────────────────

    /**
     * Validate an extracted invoice for arithmetic consistency.
     * Never silently alters values – only reports warnings.
     */
    fun validate(data: ParsedInvoiceData): InvoiceValidationResult {
        val warnings = mutableListOf<String>()
        warnings.addAll(data.validationWarnings)

        var hasCritical = false

        // Check line items sum
        val lineSum = data.items.sumOf { it.lineTotalPaise }
        if (lineSum > 0 && data.subtotalPaise > 0) {
            val diff = kotlin.math.abs(lineSum - data.subtotalPaise)
            if (diff > ROUNDING_TOLERANCE_PAISE) {
                warnings.add("Line items sum (₹${lineSum / 100.0}) ≠ subtotal (₹${data.subtotalPaise / 100.0})")
                hasCritical = true
            }
        }

        // Check grand total arithmetic
        if (data.grandTotalPaise > 0 && data.subtotalPaise > 0) {
            val computed = data.subtotalPaise - data.discountPaise + data.taxPaise
            val diff = kotlin.math.abs(data.grandTotalPaise - computed)
            if (diff > ROUNDING_TOLERANCE_PAISE) {
                warnings.add("Grand total (₹${data.grandTotalPaise / 100.0}) ≠ subtotal - discount + tax (₹${computed / 100.0})")
                hasCritical = true
            }
        }

        // GSTIN format check
        if (data.supplierGstin.isNotBlank() && !isValidGstin(data.supplierGstin)) {
            warnings.add("GSTIN '${data.supplierGstin}' may be incorrectly recognized")
        }

        return InvoiceValidationResult(
            isValid = !hasCritical,
            warnings = warnings,
            hasCriticalMismatch = hasCritical
        )
    }

    private fun isValidGstin(gstin: String): Boolean =
        gstin.length == 15 && gstinPattern.matcher(gstin).matches()
}
