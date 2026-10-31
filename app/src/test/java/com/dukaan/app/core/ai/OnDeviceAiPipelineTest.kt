package com.dukaan.app.core.ai

import com.dukaan.app.core.ai.contracts.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OnDeviceAiPipelineTest {

    private lateinit var validator: InvoiceValidator

    @Before
    fun setUp() {
        validator = InvoiceValidator()
    }

    @Test
    fun `OCRField effectiveValue gives precedence to user edits`() {
        val originalField = OCRField(
            value = 5000L,
            rawText = "50.00",
            confidence = 0.82f,
            source = ExtractionSource.ON_DEVICE_MLKIT
        )

        assertEquals(5000L, originalField.effectiveValue)
        assertFalse(originalField.isUserModified)

        val editedField = originalField.copy(
            isUserModified = true,
            userCorrectedValue = 5500L
        )

        assertEquals(5500L, editedField.effectiveValue)
        assertTrue(editedField.isUserModified)
    }

    @Test
    fun `OCRProduct arithmetic validation checks qty times price minus discount`() {
        val validProduct = OCRProduct(
            id = "prod_1",
            productName = OCRField("Tata Salt 1kg"),
            quantity = OCRField(2.0),
            unitPricePaise = OCRField(2800L), // ₹28.00
            discountPaise = OCRField(0L),
            lineTotalPaise = OCRField(5600L) // ₹56.00
        )

        assertTrue(validProduct.isArithmeticConsistent())

        val inconsistentProduct = validProduct.copy(
            lineTotalPaise = OCRField(10000L) // Incorrect ₹100.00
        )

        assertFalse(inconsistentProduct.isArithmeticConsistent())
    }

    @Test
    fun `InvoiceValidator passes mathematically consistent invoice draft`() {
        val item1 = OCRProduct(
            id = "item_1",
            productName = OCRField("Aashirvaad Atta 5kg"),
            quantity = OCRField(1.0),
            unitPricePaise = OCRField(24000L), // ₹240.00
            lineTotalPaise = OCRField(24000L)
        )
        val item2 = OCRProduct(
            id = "item_2",
            productName = OCRField("Fortune Mustard Oil 1L"),
            quantity = OCRField(2.0),
            unitPricePaise = OCRField(14000L), // ₹140.00
            discountPaise = OCRField(1000L), // ₹10.00 discount
            lineTotalPaise = OCRField(27000L) // 2 * 140 - 10 = 270.00
        )

        val draft = InvoiceDraft(
            id = "inv_test_1",
            supplierName = "Sharma Wholesale Traders",
            supplierGstin = "07AAAAA0000A1Z5",
            supplierPhone = "9876543210",
            invoiceNumber = "INV-2026-001",
            invoiceDate = System.currentTimeMillis(),
            dueDate = null,
            items = listOf(item1, item2),
            subtotalPaise = 51000L, // 240 + 270 = 510
            discountPaise = 0L,
            taxPaise = 0L,
            grandTotalPaise = 51000L,
            imagePath = "/data/local/tmp/inv1.jpg",
            rawOcrText = "Sample text",
            extractionSource = ExtractionSource.ON_DEVICE_VLM,
            validationResult = InvoiceValidationResult.VALID
        )

        val result = validator.validateDraft(draft)
        assertTrue("Draft should be valid: ${result.warnings}", result.isValid)
        assertFalse(result.hasCriticalMismatch)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `InvoiceValidator catches line item math mismatch`() {
        val mismatchedItem = OCRProduct(
            id = "item_err",
            productName = OCRField("Basmati Rice 10kg"),
            quantity = OCRField(3.0),
            unitPricePaise = OCRField(80000L), // ₹800.00 (expected 3 * 800 = ₹2400.00)
            lineTotalPaise = OCRField(300000L) // Faulty ₹3000.00 on bill
        )

        val draft = InvoiceDraft(
            id = "inv_err_1",
            supplierName = "Gupta Trading Co.",
            supplierGstin = null,
            supplierPhone = null,
            invoiceNumber = "B-492",
            invoiceDate = System.currentTimeMillis(),
            dueDate = null,
            items = listOf(mismatchedItem),
            subtotalPaise = 300000L,
            discountPaise = 0L,
            taxPaise = 0L,
            grandTotalPaise = 300000L,
            imagePath = "/data/local/tmp/err.jpg",
            rawOcrText = "",
            extractionSource = ExtractionSource.ON_DEVICE_MLKIT,
            validationResult = InvoiceValidationResult.VALID
        )

        val result = validator.validateDraft(draft)
        assertFalse(result.isValid)
        assertTrue(result.hasCriticalMismatch)
        assertTrue(result.warnings.any { it.contains("Math mismatch") })
    }

    @Test
    fun `InvoiceValidator catches grand total calculation discrepancy`() {
        val item = OCRProduct(
            id = "item_1",
            productName = OCRField("Good Day Biscuits"),
            quantity = OCRField(10.0),
            unitPricePaise = OCRField(2000L),
            lineTotalPaise = OCRField(20000L)
        )

        // Subtotal = 200, Tax = 10, but Grand Total = 300 (discrepancy)
        val draft = InvoiceDraft(
            id = "inv_total_err",
            supplierName = "Aggarwal Store",
            supplierGstin = null,
            supplierPhone = null,
            invoiceNumber = "INV-99",
            invoiceDate = System.currentTimeMillis(),
            dueDate = null,
            items = listOf(item),
            subtotalPaise = 20000L,
            discountPaise = 0L,
            taxPaise = 1000L,
            grandTotalPaise = 30000L, // Mismatch: 200 + 10 != 300
            imagePath = "/data/local/tmp/total_err.jpg",
            rawOcrText = "",
            extractionSource = ExtractionSource.ON_DEVICE_MLKIT,
            validationResult = InvoiceValidationResult.VALID
        )

        val result = validator.validateDraft(draft)
        assertFalse(result.isValid)
        assertTrue(result.hasCriticalMismatch)
        assertTrue(result.warnings.any { it.contains("Grand total discrepancy") })
    }

    @Test
    fun `InvoiceScanMode defines distinct scan profiles`() {
        val modes = InvoiceScanMode.entries
        assertTrue(modes.contains(InvoiceScanMode.AUTO))
        assertTrue(modes.contains(InvoiceScanMode.HANDWRITTEN))
        assertTrue(modes.contains(InvoiceScanMode.PRINTED))
        assertTrue(modes.contains(InvoiceScanMode.THERMAL_RECEIPT))
    }
}

