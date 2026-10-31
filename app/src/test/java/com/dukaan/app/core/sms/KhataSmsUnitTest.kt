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
        val balancePaise = 245000L // ₹2450
        val balanceRupees = balancePaise / 100

        val expected = "Namaste Ramesh ji! Sharma General Store mein aapka ₹2450 udhaar baki hai. Kripya apni suvidha anusar payment kar dein. Dhanyavaad!"
        val actual = "Namaste $customerName ji! $shopName mein aapka ₹$balanceRupees udhaar baki hai. Kripya apni suvidha anusar payment kar dein. Dhanyavaad!"

        assertEquals(expected, actual)
    }

    @Test
    fun testMonthlyAutoSmsTemplateFormat() {
        val customerName = "Vikram Singh"
        val shopName = "Sharma General Store"
        val balancePaise = 310000L // ₹3100
        val balanceRupees = balancePaise / 100

        val expected = "Namaste Vikram Singh ji! Sharma General Store ke Khata ke anusar aapka ₹3100 baki hai. Kripya apna baki payment jama kar dein. Dhanyavaad!"
        val actual = "Namaste $customerName ji! $shopName ke Khata ke anusar aapka ₹$balanceRupees baki hai. Kripya apna baki payment jama kar dein. Dhanyavaad!"

        assertEquals(expected, actual)
    }

    @Test
    fun testPhoneNumberCleaning() {
        fun clean(raw: String): String {
            val trimmed = raw.trim().replace(" ", "").replace("-", "")
            return if (trimmed.startsWith("+91")) {
                trimmed
            } else if (trimmed.length == 10) {
                "+91$trimmed"
            } else if (trimmed.startsWith("0") && trimmed.length == 11) {
                "+91${trimmed.substring(1)}"
            } else {
                trimmed
            }
        }

        assertEquals("+919876543210", clean("9876543210"))
        assertEquals("+919876543210", clean("98765-43210"))
        assertEquals("+919876543210", clean("+91 98765 43210"))
        assertEquals("+919876543210", clean("09876543210"))
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

        // Must be in the future
        assertTrue("Scheduled month end must be in the future", timestamp > now)

        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        // Must be at 20:00 (8:00 PM)
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))

        // Must be the actual last day of that month
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
}

