package com.dukaan.app.core.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.domain.repository.KhataSmsRepository
import com.dukaan.app.domain.repository.ShopRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class MonthlyKhataSmsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val khataSmsRepository: KhataSmsRepository,
    private val shopRepository: ShopRepository,
    private val preferences: DukaanPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "monthly_khata_sms_work"
        private const val TAG = "MonthlyKhataSmsWorker"

        /**
         * Computes the timestamp for the last day of the calendar month at 20:00 (8:00 PM)
         * in the device's local timezone.
         */
        fun calculateNextMonthEndTimestamp(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 20)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, maxDay)

            val now = System.currentTimeMillis()
            if (cal.timeInMillis <= now) {
                // If today is already past the 8 PM trigger on the last day, move to next month's end
                cal.add(Calendar.MONTH, 1)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            return cal.timeInMillis
        }

        /**
         * Schedules automatic month-end SMS execution with WorkManager.
         */
        suspend fun schedule(context: Context, preferences: DukaanPreferences) {
            val targetTimestamp = calculateNextMonthEndTimestamp()
            val initialDelay = maxOf(targetTimestamp - System.currentTimeMillis(), 0L)

            preferences.setNextMonthlySmsDate(targetTimestamp)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MonthlyKhataSmsWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "Scheduled next monthly Khata SMS worker for: ${Date(targetTimestamp)} (delay: ${initialDelay / 1000 / 60} minutes)")
        }

        /**
         * Cancels future automatic monthly SMS work.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled monthly Khata SMS worker")
        }
    }

    override suspend fun doWork(): Result {
        val isEnabled = preferences.autoMonthlySms.first()
        if (!isEnabled) {
            Log.d(TAG, "Automatic monthly SMS reminders are turned OFF. Aborting work.")
            return Result.success()
        }

        val monthYear = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val shopProfile = shopRepository.getShopProfileOnce()
        val shopName = shopProfile?.shopName?.trim().let { if (!it.isNullOrBlank()) it else "Dukaan" }

        Log.d(TAG, "Starting monthly Khata SMS batch for monthYear=$monthYear, shopName=$shopName")

        val eligibleCustomers = khataSmsRepository.getCustomersWithOutstanding()
        Log.d(TAG, "Found ${eligibleCustomers.size} customers with outstanding balances > ₹0")

        var successCount = 0
        var failCount = 0

        for (customer in eligibleCustomers) {
            val phone = customer.phoneNumber?.trim() ?: ""
            if (phone.isBlank()) {
                Log.w(TAG, "Skipping customer ${customer.name} (no phone number)")
                continue
            }

            try {
                val sendResult = khataSmsRepository.sendMonthlyReminderToCustomer(
                    customer = customer,
                    shopName = shopName,
                    monthYear = monthYear
                )

                if (sendResult.isSuccess) {
                    successCount++
                    Log.d(TAG, "Monthly reminder sent to ${customer.name} ($phone)")
                } else {
                    failCount++
                    Log.w(TAG, "Failed sending to ${customer.name}: ${sendResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "Error sending monthly reminder to ${customer.name}: ${e.message}")
            }

            // Reasonable delay between messages to prevent carrier spam detection / queue congestion
            delay(2500L)
        }

        Log.d(TAG, "Completed monthly SMS run: $successCount sent, $failCount failed out of ${eligibleCustomers.size} eligible customers")
        preferences.setLastMonthlySmsRun(System.currentTimeMillis())

        // Re-schedule for the next month's end
        schedule(context, preferences)

        return Result.success()
    }
}

