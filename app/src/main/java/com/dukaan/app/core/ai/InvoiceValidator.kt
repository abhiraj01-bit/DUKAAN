package com.dukaan.app.core.ai

import com.dukaan.app.core.ai.contracts.InvoiceDraft
import com.dukaan.app.core.ai.contracts.InvoiceValidationResult
import com.dukaan.app.core.ai.contracts.OCRProduct
import com.dukaan.app.core.util.CurrencyFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Validates financial and arithmetic consistency for invoice drafts.
 *
 * Rules:
 * - Computations are strictly conducted using 64-bit Long paise (1 INR = 100 paise) to prevent floating-point drift.
 * - Line item check: (Quantity * UnitPrice) - Discount == LineTotal (within 200 paise / ₹2 tolerance).
 * - Overall balance check: Subtotal - Discount + Tax == GrandTotal (within 200 paise / ₹2 tolerance).
 * - Flags missing critical fields (Invoice number, Supplier name, or Empty item list).
 */
@Singleton
class InvoiceValidator @Inject constructor() {

    companion object {
        const val ROUNDING_TOLERANCE_PAISE: Long = 200L // ₹2 tolerance for rounding variations
    }

    fun validateDraft(draft: InvoiceDraft): InvoiceValidationResult {
        val warnings = mutableListOf<String>()
        var hasCriticalMismatch = false

        // 1. Check critical identity fields
        if (draft.supplierName.isBlank()) {
            warnings.add("Supplier name could not be identified.")
        }
        if (draft.invoiceNumber.isBlank()) {
            warnings.add("Invoice number is missing or unreadable.")
        }
        if (draft.items.isEmpty()) {
            warnings.add("No product line items were detected on the bill.")
        }

        // 2. Validate individual line items arithmetic
        draft.items.forEachIndexed { index, item ->
            val qty = item.quantity.effectiveValue ?: 0.0
            val unitPrice = item.unitPricePaise.effectiveValue ?: 0L
            val lineTotal = item.lineTotalPaise.effectiveValue ?: 0L
            val discount = item.discountPaise.effectiveValue ?: 0L

            if (qty <= 0.0) {
                warnings.add("Item #${index + 1} (${item.productName.effectiveValue ?: "Unknown"}): Quantity is 0 or negative.")
            }
            if (unitPrice <= 0L && lineTotal <= 0L) {
                warnings.add("Item #${index + 1} (${item.productName.effectiveValue ?: "Unknown"}): Missing price.")
            }

            // Expected line total
            val expectedLineTotal = (qty * unitPrice).toLong() - discount
            if (unitPrice > 0L && lineTotal > 0L && abs(expectedLineTotal - lineTotal) > ROUNDING_TOLERANCE_PAISE) {
                val diffRupees = abs(expectedLineTotal - lineTotal) / 100.0
                warnings.add(
                    "Item #${index + 1} (${item.productName.effectiveValue ?: "Unknown"}): " +
                    "Math mismatch (Qty $qty × ₹${CurrencyFormatter.formatPaise(unitPrice)} - Disc ₹${CurrencyFormatter.formatPaise(discount)} = ₹${CurrencyFormatter.formatPaise(expectedLineTotal)}, but bill printed ₹${CurrencyFormatter.formatPaise(lineTotal)}, diff: ₹$diffRupees)."
                )
                hasCriticalMismatch = true
            }
        }

        // 3. Validate overall sum of items vs Subtotal / Grand Total
        val sumOfItems = draft.items.sumOf { it.lineTotalPaise.effectiveValue ?: 0L }
        if (draft.subtotalPaise > 0L && sumOfItems > 0L) {
            val subtotalDiff = abs(sumOfItems - draft.subtotalPaise)
            if (subtotalDiff > ROUNDING_TOLERANCE_PAISE) {
                warnings.add(
                    "Item totals sum (₹${CurrencyFormatter.formatPaise(sumOfItems)}) differs from printed subtotal (₹${CurrencyFormatter.formatPaise(draft.subtotalPaise)})."
                )
            }
        }

        // 4. Validate Subtotal - Discount + Tax == Grand Total
        val calculatedGrandTotal = draft.subtotalPaise - draft.discountPaise + draft.taxPaise
        if (draft.grandTotalPaise > 0L && draft.subtotalPaise > 0L) {
            val totalDiff = abs(calculatedGrandTotal - draft.grandTotalPaise)
            if (totalDiff > ROUNDING_TOLERANCE_PAISE) {
                warnings.add(
                    "Grand total discrepancy: Subtotal (₹${CurrencyFormatter.formatPaise(draft.subtotalPaise)}) " +
                    "- Disc (₹${CurrencyFormatter.formatPaise(draft.discountPaise)}) " +
                    "+ Tax (₹${CurrencyFormatter.formatPaise(draft.taxPaise)}) = ₹${CurrencyFormatter.formatPaise(calculatedGrandTotal)}, " +
                    "printed Grand Total = ₹${CurrencyFormatter.formatPaise(draft.grandTotalPaise)}."
                )
                hasCriticalMismatch = true
            }
        } else if (draft.grandTotalPaise <= 0L && sumOfItems > 0L) {
            warnings.add("Grand total was not detected on bill. Suggested total: ₹${CurrencyFormatter.formatPaise(sumOfItems)}.")
        }

        return InvoiceValidationResult(
            isValid = warnings.isEmpty(),
            warnings = warnings,
            hasCriticalMismatch = hasCriticalMismatch,
            arithmeticChecked = true
        )
    }

    /**
     * Checks arithmetic consistency on a single line item.
     */
    fun isLineItemConsistent(item: OCRProduct): Boolean {
        val qty = item.quantity.effectiveValue ?: return false
        val unitPrice = item.unitPricePaise.effectiveValue ?: return false
        val lineTotal = item.lineTotalPaise.effectiveValue ?: return false
        val discount = item.discountPaise.effectiveValue ?: 0L

        val expected = (qty * unitPrice).toLong() - discount
        return abs(expected - lineTotal) <= ROUNDING_TOLERANCE_PAISE
    }
}

