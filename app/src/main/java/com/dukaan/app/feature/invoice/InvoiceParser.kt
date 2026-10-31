package com.dukaan.app.feature.invoice

import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.domain.model.InvoiceItem
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class ParsedInvoiceData(
    val supplierName: String = "",
    val invoiceNumber: String = "",
    val invoiceDate: Long = System.currentTimeMillis(),
    val items: List<InvoiceItem> = emptyList(),
    val grandTotalPaise: Long = 0L,
    val rawText: String = ""
)

object InvoiceParser {

    private val invoiceNumPatterns = listOf(
        Pattern.compile("""(?:inv(?:oice)?|bill)\s*(?:no|num|#)?[.:\s]*([a-zA-Z0-9\-_/]+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:b\.?no|bill\s*number)[.:\s]*([a-zA-Z0-9\-_/]+)""", Pattern.CASE_INSENSITIVE)
    )

    private val totalPatterns = listOf(
        Pattern.compile("""(?:grand\s*total|net\s*amount|total\s*amount|final\s*total|total)[.:\s]*(?:\u20B9|Rs\.?|INR)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:\u20B9|Rs\.?|INR)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    )

    fun parse(ocrText: String, invoiceId: String = UuidGenerator.generate()): ParsedInvoiceData {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var detectedSupplier = ""
        var detectedInvoiceNum = ""
        var detectedDate = System.currentTimeMillis()
        var detectedTotalPaise = 0L
        val parsedItems = mutableListOf<InvoiceItem>()

        // 1. Detect Supplier Name (First 1-3 prominent lines usually have the business name)
        for (i in 0 until minOf(4, lines.size)) {
            val line = lines[i]
            if (!line.contains("invoice", ignoreCase = true) &&
                !line.contains("tax", ignoreCase = true) &&
                !line.contains("gst", ignoreCase = true) &&
                line.length >= 3) {
                detectedSupplier = line
                break
            }
        }

        // 2. Detect Invoice Number & Dates
        for (line in lines) {
            if (detectedInvoiceNum.isEmpty()) {
                for (p in invoiceNumPatterns) {
                    val m = p.matcher(line)
                    if (m.find()) {
                        val num = m.group(1)?.trim()
                        if (!num.isNullOrBlank() && num.length >= 2) {
                            detectedInvoiceNum = num
                            break
                        }
                    }
                }
            }

            // Detect Date
            for (df in dateFormats) {
                try {
                    val dateRegex = Pattern.compile("""(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4})""")
                    val dm = dateRegex.matcher(line)
                    if (dm.find()) {
                        val dateStr = dm.group(1)
                        if (dateStr != null) {
                            val parsed = df.parse(dateStr)
                            if (parsed != null) {
                                detectedDate = parsed.time
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Detect Total
            for (tp in totalPatterns) {
                val tm = tp.matcher(line)
                if (tm.find()) {
                    val amtStr = tm.group(1)
                    if (amtStr != null) {
                        val paise = CurrencyFormatter.rupeesToPaise(amtStr)
                        if (paise > detectedTotalPaise) {
                            detectedTotalPaise = paise
                        }
                    }
                }
            }
        }

        // 3. Line items heuristics: [Item Name] [Quantity] [Unit/Price] [Total]
        val lineItemPattern = Pattern.compile(
            """^(.*?)\s+(\d+(?:\.\d+)?)\s*(?:pcs|kg|gm|ltr|box|pkt|bag)?\s+(?:x\s*)?(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""",
            Pattern.CASE_INSENSITIVE
        )

        for (line in lines) {
            val m = lineItemPattern.matcher(line)
            if (m.find()) {
                val name = m.group(1)?.trim() ?: ""
                val qty = m.group(2)?.toDoubleOrNull() ?: 1.0
                val price = (m.group(3)?.toDoubleOrNull() ?: 0.0)
                val lineTotal = (m.group(4)?.toDoubleOrNull() ?: (qty * price))
                if (name.isNotBlank() && name.length > 2) {
                    parsedItems.add(
                        InvoiceItem(
                            id = UuidGenerator.generate(),
                            invoiceId = invoiceId,
                            productName = name,
                            quantity = qty,
                            purchasePricePaise = (price * 100).toLong(),
                            lineTotalPaise = (lineTotal * 100).toLong()
                        )
                    )
                }
            }
        }

        return ParsedInvoiceData(
            supplierName = detectedSupplier.trim(),
            invoiceNumber = detectedInvoiceNum.trim(),
            invoiceDate = detectedDate,
            items = parsedItems,
            grandTotalPaise = detectedTotalPaise,
            rawText = ocrText
        )
    }
}
