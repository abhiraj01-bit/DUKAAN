package com.dukaan.app.feature.invoice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InvoiceScannerUnitTest {

    @Test
    fun testGeminiModelManager_validatesModelNames() {
        val manager = GeminiModelManager()
        assertTrue(manager.validateModelName("gemini-1.5-flash"))
        assertTrue(manager.validateModelName("gemini-2.0-flash"))
        assertTrue(manager.validateModelName("gemini-1.5-pro"))
        assertTrue(manager.validateModelName("custom_model-1"))

        assertFalse(manager.validateModelName(""))
        assertFalse(manager.validateModelName("   "))
        assertFalse(manager.validateModelName("ab")) // too short
        assertFalse(manager.validateModelName("model with spaces"))
        assertFalse(manager.validateModelName("model;injection"))
    }

    @Test
    fun testGeminiModelManager_ordersCandidatesWithPrimaryFirst() {
        val manager = GeminiModelManager()
        val candidates = manager.getCandidateModels("gemini-1.5-pro", fallbackEnabled = true)

        assertEquals("gemini-1.5-pro", candidates.first())
        assertTrue(candidates.contains("gemini-1.5-flash"))
        assertTrue(candidates.contains("gemini-2.0-flash"))
        // No duplicates
        assertEquals(candidates.size, candidates.distinct().size)
    }

    @Test
    fun testGeminiModelManager_fallbackDisabledReturnsOnlyPrimary() {
        val manager = GeminiModelManager()
        val candidates = manager.getCandidateModels("gemini-custom", fallbackEnabled = false)

        assertEquals(1, candidates.size)
        assertEquals("gemini-custom", candidates[0])
    }

    @Test
    fun testGeminiModelManager_errorFallbackDecision() {
        val manager = GeminiModelManager()

        // Recoverable errors SHOULD trigger fallback
        assertTrue(manager.shouldAttemptFallback(GeminiError.QuotaExceeded()))
        assertTrue(manager.shouldAttemptFallback(GeminiError.RateLimited()))
        assertTrue(manager.shouldAttemptFallback(GeminiError.ModelNotFound("gemini-old")))
        assertTrue(manager.shouldAttemptFallback(GeminiError.ServerError(503)))
        assertTrue(manager.shouldAttemptFallback(GeminiError.Timeout()))
        assertTrue(manager.shouldAttemptFallback(GeminiError.InvalidJson("Malformed json")))

        // Non-recoverable errors MUST NOT trigger fallback
        assertFalse(manager.shouldAttemptFallback(GeminiError.InvalidApiKey()))
        assertFalse(manager.shouldAttemptFallback(GeminiError.PermissionDenied()))
        assertFalse(manager.shouldAttemptFallback(GeminiError.ImageTooLarge()))
        assertFalse(manager.shouldAttemptFallback(GeminiError.ContentBlocked("SAFETY")))
        assertFalse(manager.shouldAttemptFallback(GeminiError.NoApiKey()))
    }

    @Test
    fun testRetryPolicy_haltsImmediatelyOnNonRetryableError() = runTest {
        val policy = RetryPolicy(maxRetries = 3, initialDelayMs = 10)
        var executionCount = 0

        val result = policy.execute<String> {
            executionCount++
            Result.failure(GeminiException(GeminiError.InvalidApiKey()))
        }

        assertTrue(result.isFailure)
        assertEquals(1, executionCount) // Never retried
    }

    @Test
    fun testRetryPolicy_succeedsOnSecondAttempt() = runTest {
        val policy = RetryPolicy(maxRetries = 3, initialDelayMs = 10)
        var attempts = 0

        val result = policy.execute { attempt ->
            attempts++
            if (attempt == 1) {
                Result.failure(GeminiException(GeminiError.ServerError(503)))
            } else {
                Result.success("Success on attempt $attempt")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("Success on attempt 2", result.getOrNull())
        assertEquals(2, attempts)
    }

    @Test
    fun testRetryPolicy_rethrowsCancellationException() = runTest {
        val policy = RetryPolicy(maxRetries = 3, initialDelayMs = 10)
        try {
            policy.execute<String> {
                throw CancellationException("User navigated away")
            }
            fail("Expected CancellationException to be rethrown")
        } catch (ce: CancellationException) {
            assertEquals("User navigated away", ce.message)
        }
    }

    @Test
    fun testInvoiceParser_noFakeSupplierOrInvoiceNumber() {
        val emptyOcr = ""
        val parsed = InvoiceParser.parse(emptyOcr, "inv_test")

        // Must not invent fake names
        assertEquals("", parsed.supplierName)
        assertEquals("", parsed.invoiceNumber)
        assertEquals(0L, parsed.grandTotalPaise)
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun testInvoiceParser_extractsRealLineItemsCorrectly() {
        val sampleOcr = """
            Ramesh Supermarket
            Invoice No: INV-9982
            Date: 12/10/2026
            
            Sugar 5 kg 45 225
            Rice 10 kg 60 600
            
            Total: 825
        """.trimIndent()

        val parsed = InvoiceParser.parse(sampleOcr, "inv_sample")

        assertEquals("Ramesh Supermarket", parsed.supplierName)
        assertEquals("INV-9982", parsed.invoiceNumber)
        assertEquals(82500L, parsed.grandTotalPaise)
        assertEquals(2, parsed.items.size)

        assertEquals("Sugar", parsed.items[0].productName)
        assertEquals(5.0, parsed.items[0].quantity, 0.001)
        assertEquals(4500L, parsed.items[0].purchasePricePaise)
        assertEquals(22500L, parsed.items[0].lineTotalPaise)

        assertEquals("Rice", parsed.items[1].productName)
        assertEquals(10.0, parsed.items[1].quantity, 0.001)
        assertEquals(6000L, parsed.items[1].purchasePricePaise)
        assertEquals(60000L, parsed.items[1].lineTotalPaise)
    }
}

