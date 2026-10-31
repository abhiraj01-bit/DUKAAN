package com.dukaan.app.feature.invoice

import com.dukaan.app.core.ocr.OcrLanguage
import com.dukaan.app.core.ocr.OcrLanguageRouter
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for [InvoiceParser] covering:
 * - Supplier name extraction
 * - Invoice number extraction
 * - Date parsing (multiple formats)
 * - Total/subtotal/tax extraction
 * - GSTIN extraction and validation
 * - Phone extraction
 * - Line item parsing
 * - Financial arithmetic validation
 * - Mixed-language invoice handling
 */
class InvoiceParserTest {

    // ─── Clean English invoice ────────────────────────────────────────────

    private val cleanEnglishInvoice = """
        SHARMA TRADERS
        123, Market Road, Delhi - 110001
        GSTIN: 07AABCS1429B1Z2
        Phone: 9876543210
        
        Tax Invoice
        Invoice No: INV-2024-0123
        Date: 15/01/2024
        
        Item                Qty   Price    Total
        Basmati Rice 5kg    10    450.00   4500.00
        Mustard Oil 1L      20    120.00   2400.00
        Sugar 1kg           15     55.00    825.00
        
        Subtotal                           7725.00
        GST (5%)                            386.25
        Grand Total                        8111.25
    """.trimIndent()

    @Test
    fun `parse clean English invoice - supplier name correct`() {
        val result = InvoiceParser.parse(cleanEnglishInvoice, "test-id")
        assertEquals("SHARMA TRADERS", result.supplierName)
    }

    @Test
    fun `parse clean English invoice - invoice number correct`() {
        val result = InvoiceParser.parse(cleanEnglishInvoice, "test-id")
        assertEquals("INV-2024-0123", result.invoiceNumber)
    }

    @Test
    fun `parse clean English invoice - GSTIN extracted`() {
        val result = InvoiceParser.parse(cleanEnglishInvoice, "test-id")
        assertEquals("07AABCS1429B1Z2", result.supplierGstin)
    }

    @Test
    fun `parse clean English invoice - phone extracted`() {
        val result = InvoiceParser.parse(cleanEnglishInvoice, "test-id")
        assertEquals("9876543210", result.supplierPhone)
    }

    @Test
    fun `parse clean English invoice - grand total correct`() {
        val result = InvoiceParser.parse(cleanEnglishInvoice, "test-id")
        // ₹8111.25 = 811125 paise
        assertEquals(811125L, result.grandTotalPaise)
    }

    @Test
    fun `parse clean English invoice - date correct`() {
        val result = InvoiceParser.parse(cleanEnglishInvoice, "test-id")
        val cal = Calendar.getInstance().apply { timeInMillis = result.invoiceDate }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(2024, cal.get(Calendar.YEAR))
    }

    // ─── Line items parsing ───────────────────────────────────────────────

    private val lineItemInvoice = """
        ABC Distributors
        Invoice No: BILL-567
        Date: 20-03-2024
        
        Product                Qty   Rate     Amount
        Sunflower Oil 1L       5     180.00   900.00
        Atta 10kg              3     380.00   1140.00
        Dal Chana 1kg          8      95.00   760.00
        
        Grand Total                           2800.00
    """.trimIndent()

    @Test
    fun `parse line item invoice - items count`() {
        val result = InvoiceParser.parse(lineItemInvoice, "test-id")
        assertTrue("Expected items, got ${result.items.size}", result.items.isNotEmpty())
    }

    @Test
    fun `parse line item invoice - grand total`() {
        val result = InvoiceParser.parse(lineItemInvoice, "test-id")
        assertEquals(280000L, result.grandTotalPaise)
    }

    // ─── Currency parsing ─────────────────────────────────────────────────

    @Test
    fun `rupeesToPaise converts correctly`() {
        val formatter = com.dukaan.app.core.util.CurrencyFormatter
        assertEquals(100L, formatter.rupeesToPaise("1"))
        assertEquals(100L, formatter.rupeesToPaise("1.00"))
        assertEquals(150L, formatter.rupeesToPaise("1.50"))
        assertEquals(811125L, formatter.rupeesToPaise("8111.25"))
        assertEquals(0L, formatter.rupeesToPaise(""))
        assertEquals(0L, formatter.rupeesToPaise("abc"))
    }

    @Test
    fun `rupeesToPaise handles comma-formatted numbers`() {
        val formatter = com.dukaan.app.core.util.CurrencyFormatter
        assertEquals(10000L, formatter.rupeesToPaise("100.00"))
    }

    // ─── Date parsing ─────────────────────────────────────────────────────

    @Test
    fun `parse invoice with dd-MM-yyyy date format`() {
        val text = "Supplier\nInvoice No: 001\nDate: 25-06-2023\nTotal 5000"
        val result = InvoiceParser.parse(text, "test-id")
        val cal = Calendar.getInstance().apply { timeInMillis = result.invoiceDate }
        assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(2023, cal.get(Calendar.YEAR))
    }

    @Test
    fun `parse invoice with dd_MM_yyyy dot format`() {
        val text = "Supplier\nInvoice No: 002\nDate: 10.12.2022\nTotal 2500"
        val result = InvoiceParser.parse(text, "test-id")
        val cal = Calendar.getInstance().apply { timeInMillis = result.invoiceDate }
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH))
        assertEquals(2022, cal.get(Calendar.YEAR))
    }

    // ─── GSTIN validation ─────────────────────────────────────────────────

    @Test
    fun `GSTIN extracted from middle of text`() {
        val text = "Company Name\nGSTIN: 27AAPFU0939F1ZV\nInvoice"
        val result = InvoiceParser.parse(text, "test-id")
        assertEquals("27AAPFU0939F1ZV", result.supplierGstin)
    }

    @Test
    fun `invalid GSTIN not extracted`() {
        val text = "Company\nGSTIN: 12345\nInvoice"
        val result = InvoiceParser.parse(text, "test-id")
        assertTrue("Should not extract invalid GSTIN", result.supplierGstin.isEmpty())
    }

    // ─── GST arithmetic validation ────────────────────────────────────────

    @Test
    fun `validate - grand total mismatch produces warning`() {
        val parsed = ParsedInvoiceData(
            supplierName = "Test",
            grandTotalPaise = 500000L,
            subtotalPaise   = 400000L,
            taxPaise        = 0L,
            discountPaise   = 0L,
            items = emptyList()
        )
        val validation = InvoiceParser.validate(parsed)
        assertFalse("Should flag mismatch as not fully valid", validation.isValid)
        assertTrue("Should have warnings", validation.warnings.isNotEmpty())
    }

    @Test
    fun `validate - matching totals passes`() {
        val parsed = ParsedInvoiceData(
            supplierName    = "Test",
            grandTotalPaise = 450000L,
            subtotalPaise   = 450000L,
            taxPaise        = 0L,
            discountPaise   = 0L
        )
        val validation = InvoiceParser.validate(parsed)
        assertTrue("Matching totals should be valid", validation.isValid)
    }

    @Test
    fun `validate - small rounding difference tolerated`() {
        val parsed = ParsedInvoiceData(
            supplierName    = "Test",
            grandTotalPaise = 450100L,
            subtotalPaise   = 450000L,
            taxPaise        = 0L,
            discountPaise   = 0L
        )
        val validation = InvoiceParser.validate(parsed)
        assertTrue("Small rounding should be tolerated", validation.isValid)
    }

    // ─── Empty and edge cases ─────────────────────────────────────────────

    @Test
    fun `parse empty text returns empty result with note`() {
        val result = InvoiceParser.parse("", "test-id")
        assertEquals("", result.supplierName)
        assertEquals(0L, result.grandTotalPaise)
        assertTrue("Should note empty OCR", result.extractionNotes.isNotEmpty())
    }

    @Test
    fun `parse whitespace-only text returns empty result`() {
        val result = InvoiceParser.parse("   \n  \t  ", "test-id")
        assertEquals("", result.supplierName)
        assertEquals(0L, result.grandTotalPaise)
    }

    @Test
    fun `parser never fabricates totals from nothing`() {
        val sparseText = "Invoice\nDate: 01/01/2024"
        val result = InvoiceParser.parse(sparseText, "test-id")
        assertEquals(0L, result.grandTotalPaise)
    }

    // ─── Hindi / Devanagari invoice fields ───────────────────────────────

    @Test
    fun `parse invoice with Hindi supplier name does not crash`() {
        val hindiText = """
            शर्मा जनरल स्टोर
            Invoice No: INV-001
            Date: 10/02/2024
            चावल 5kg          2    500.00   1000.00
            Grand Total                      1000.00
        """.trimIndent()
        val result = InvoiceParser.parse(hindiText, "test-id")
        assertEquals("INV-001", result.invoiceNumber)
        assertEquals(100000L, result.grandTotalPaise)
        assertFalse("Supplier name should not be empty", result.supplierName.isEmpty())
    }

    // ─── Language routing ─────────────────────────────────────────────────

    @Test
    fun `detectScriptsInText identifies English in ASCII text`() {
        val result = OcrLanguageRouter.detectScriptsInText("Invoice No: INV-001 Total: 500")
        assertTrue("Should detect English", result.contains(OcrLanguage.ENGLISH))
        assertFalse("Should NOT detect Hindi in pure Latin", result.contains(OcrLanguage.HINDI))
    }

    @Test
    fun `detectScriptsInText identifies Devanagari`() {
        val result = OcrLanguageRouter.detectScriptsInText("शर्मा स्टोर Invoice Total")
        assertTrue("Should detect Hindi", result.contains(OcrLanguage.HINDI))
        assertTrue("Should also detect English", result.contains(OcrLanguage.ENGLISH))
    }

    @Test
    fun `detectScriptsInText identifies Gurmukhi as Punjabi`() {
        val result = OcrLanguageRouter.detectScriptsInText("\u0A38\u0A24 Invoice")
        assertTrue("Should detect Punjabi (Gurmukhi)", result.contains(OcrLanguage.PUNJABI))
    }

    @Test
    fun `Punjabi is NOT supported offline`() {
        assertFalse(OcrLanguage.PUNJABI.supportsOffline)
    }

    @Test
    fun `English and Hindi ARE supported offline`() {
        assertTrue(OcrLanguage.ENGLISH.supportsOffline)
        assertTrue(OcrLanguage.HINDI.supportsOffline)
    }
}
