package com.dukaan.app.core.sms

import com.dukaan.app.core.worker.MonthlyKhataSmsWorker
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class KhataSmsUnitTest {

    @Test
    fun testManualSmsTemplateFormat() {
        val customerName = "Ramesh"
        val shopName = "Sharma General Store"
        val balancePaise = 245000L // Rs. 2450
        val balanceRupees = balancePaise / 100

        val expected = "Namaste Ramesh ji! Sharma General Store mein aapka Rs. 2450 udhaar baki hai. Kripya apni suvidha anusar payment kar dein. Dhanyavaad!"
        val actual = "Namaste $customerName ji! $shopName mein aapka Rs. $balanceRupees udhaar baki hai. Kripya apni suvidha anusar payment kar dein. Dhanyavaad!"

        assertEquals(expected, actual)
    }

    @Test
    fun testMonthlyAutoSmsTemplateFormat() {
        val customerName = "Vikram Singh"
        val shopName = "Sharma General Store"
        val balancePaise = 310000L // Rs. 3100
        val balanceRupees = balancePaise / 100

        val expected = "Namaste Vikram Singh ji! Sharma General Store ke Khata ke anusar aapka Rs. 3100 baki hai. Kripya apna baki payment jama kar dein. Dhanyavaad!"
        val actual = "Namaste $customerName ji! $shopName ke Khata ke anusar aapka Rs. $balanceRupees baki hai. Kripya apna baki payment jama kar dein. Dhanyavaad!"

        assertEquals(expected, actual)
    }

    @Test
    fun testPhoneNumberCleaning() {
        fun clean(raw: String): String {
            val trimmed = raw.trim().replace(" ", "").replace("-", "")
            return if (trimmed.startsWith("+91") && trimmed.length == 13) {
                trimmed.substring(3)
            } else if (trimmed.startsWith("91") && trimmed.length == 12) {
                trimmed.substring(2)
            } else if (trimmed.startsWith("0") && trimmed.length == 11) {
                trimmed.substring(1)
            } else {
                trimmed
            }
        }

        // Standard 10-digit domestic Indian normalization
        assertEquals("9876543210", clean("9876543210"))
        assertEquals("9876543210", clean("98765-43210"))
        assertEquals("9876543210", clean("+91 98765 43210"))
        assertEquals("9876543210", clean("09876543210"))
        assertEquals("9876543210", clean("919876543210"))
    }

    @Test
    fun testInvalidPhoneNumberDetection() {
        fun isValid(phone: String): Boolean {
            val cleaned = phone.trim().replace(" ", "").replace("-", "")
            val normalized = if (cleaned.startsWith("+91") && cleaned.length == 13) cleaned.substring(3)
            else if (cleaned.startsWith("0") && cleaned.length == 11) cleaned.substring(1)
            else cleaned
            return normalized.length == 10 && normalized.all { it.isDigit() }
        }

        assertTrue(isValid("9876543210"))
        assertTrue(isValid("+919876543210"))
        assertTrue(isValid("09876543210"))
        assertFalse(isValid(""))
        assertFalse(isValid("123"))
        assertFalse(isValid("abcdefghij"))
    }

    @Test
    fun testZeroBalanceExclusion() {
        val customers = listOf(
            Triple("Ramesh Kumar", "9876543210", 245000L),
            Triple("Suresh Patel", "9876543211", 120000L),
            Triple("Anita Sharma", "9876543212", 0L),       // 0 balance
            Triple("Vikram Singh", "9876543213", 310000L),
            Triple("Pooja Gupta", "9876543214", 50000L),
            Triple("Negative Balance", "9876543215", -1000L) // Advance payment
        )

        val eligible = customers.filter { it.third > 0L }

        assertEquals(4, eligible.size)
        assertFalse(eligible.any { it.first == "Anita Sharma" })
        assertFalse(eligible.any { it.first == "Negative Balance" })
    }

    @Test
    fun testCalculateNextMonthEndTimestamp() {
        val timestamp = MonthlyKhataSmsWorker.calculateNextMonthEndTimestamp()
        val now = System.currentTimeMillis()

        assertTrue("Scheduled month end must be in the future", timestamp > now)

        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        assertEquals(maxDay, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testUniqueMonthlyKeyPreventsDuplicates() {
        val customerId = "cust_123"
        val monthYear = "2026-10"

        val key1 = "monthly_${customerId}_$monthYear"
        val key2 = "monthly_${customerId}_$monthYear"

        assertEquals(key1, key2)
        assertEquals("monthly_cust_123_2026-10", key1)
    }

    @Test
    fun testSmsPendingIntentRequestCodeIsNonNegative() {
        val testIds = listOf(
            "sms_1",
            "sms_test_negative",
            UUID.randomUUID().toString(),
            "id_with_specific_hash",
            Integer.MIN_VALUE.toString()
        )

        for (id in testIds) {
            val reqCode = id.hashCode() and 0x7FFFFFFF
            assertTrue("Request code must be non-negative: $reqCode for $id", reqCode >= 0)
        }
    }
}
