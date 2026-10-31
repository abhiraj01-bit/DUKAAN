package com.dukaan.app.data.repository

import com.dukaan.app.core.sms.SmsSendResult
import com.dukaan.app.core.sms.SmsSendingService
import com.dukaan.app.core.util.UuidGenerator
import com.dukaan.app.data.local.dao.CustomerDao
import com.dukaan.app.data.local.dao.KhataSmsDao
import com.dukaan.app.data.local.entity.CustomerEntity
import com.dukaan.app.data.local.entity.KhataSmsEntity
import com.dukaan.app.domain.repository.KhataSmsRepository
import com.dukaan.app.domain.repository.ShopRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KhataSmsRepositoryImpl @Inject constructor(
    private val khataSmsDao: KhataSmsDao,
    private val customerDao: CustomerDao,
    private val shopRepository: ShopRepository,
    private val smsSendingService: SmsSendingService
) : KhataSmsRepository {

    private val monthYearFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    override fun getSmsHistoryByCustomer(customerId: String): Flow<List<KhataSmsEntity>> {
        return khataSmsDao.getSmsHistoryByCustomer(customerId)
    }

    override suspend fun getCustomersWithOutstanding(): List<CustomerEntity> {
        return customerDao.getCustomersWithOutstandingOnce()
    }

    override suspend fun sendManualReminder(customerId: String): Result<KhataSmsEntity> {
        val customer = customerDao.getCustomerByIdOnce(customerId)
            ?: return Result.failure(IllegalArgumentException("Customer not found"))

        val phone = customer.phoneNumber?.trim() ?: ""
        if (phone.isBlank()) {
            return Result.failure(IllegalArgumentException("Customer has no phone number on record"))
        }

        if (customer.outstandingPaise <= 0L) {
            return Result.failure(IllegalStateException("Customer has zero or negative balance (₹0 udhaar)"))
        }

        val shopProfile = shopRepository.getShopProfileOnce()
        val shopName = shopProfile?.shopName?.trim().let { if (!it.isNullOrBlank()) it else "Dukaan" }

        val balanceRupees = customer.outstandingPaise / 100
        val message = "Namaste ${customer.name} ji! $shopName mein aapka ₹$balanceRupees udhaar baki hai. Kripya apni suvidha anusar payment kar dein. Dhanyavaad!"

        val smsId = UuidGenerator.generate()
        val monthYear = monthYearFormat.format(Date())

        val entity = KhataSmsEntity(
            id = smsId,
            customerId = customer.id,
            customerName = customer.name,
            phoneNumber = phone,
            message = message,
            balancePaise = customer.outstandingPaise,
            reminderType = "MANUAL",
            monthYear = monthYear,
            status = "PENDING",
            createdAt = System.currentTimeMillis(),
            uniqueKey = "manual_$smsId"
        )
        khataSmsDao.insertOrUpdate(entity)

        val sendResult = smsSendingService.sendDirectSms(
            smsId = smsId,
            destinationAddress = phone,
            messageText = message
        )

        return when (sendResult) {
            is SmsSendResult.Success -> {
                val updated = khataSmsDao.getSmsById(smsId) ?: entity
                Result.success(updated)
            }
            is SmsSendResult.Failure -> {
                khataSmsDao.updateSentStatus(
                    id = smsId,
                    status = "FAILED",
                    errorCode = sendResult.errorCode,
                    errorMessage = sendResult.reason
                )
                Result.failure(Exception(sendResult.reason))
            }
        }
    }

    override suspend fun sendMonthlyReminderToCustomer(
        customer: CustomerEntity,
        shopName: String,
        monthYear: String
    ): Result<KhataSmsEntity> {
        val phone = customer.phoneNumber?.trim() ?: ""
        if (phone.isBlank() || customer.outstandingPaise <= 0L) {
            return Result.failure(IllegalArgumentException("Customer not eligible for reminder"))
        }

        // Idempotency: skip if already sent this month
        val uniqueKey = "monthly_${customer.id}_$monthYear"
        val existing = khataSmsDao.findByUniqueKey(uniqueKey)
        if (existing != null && (existing.status == "SENT" || existing.status == "DELIVERED")) {
            return Result.success(existing)
        }

        val balanceRupees = customer.outstandingPaise / 100
        val message = "Namaste ${customer.name} ji! $shopName ke Khata ke anusar aapka ₹$balanceRupees baki hai. Kripya apna baki payment jama kar dein. Dhanyavaad!"

        val smsId = existing?.id ?: UuidGenerator.generate()
        val entity = KhataSmsEntity(
            id = smsId,
            customerId = customer.id,
            customerName = customer.name,
            phoneNumber = phone,
            message = message,
            balancePaise = customer.outstandingPaise,
            reminderType = "MONTHLY_AUTO",
            monthYear = monthYear,
            status = "PENDING",
            createdAt = System.currentTimeMillis(),
            uniqueKey = uniqueKey
        )
        khataSmsDao.insertOrUpdate(entity)

        val sendResult = smsSendingService.sendDirectSms(
            smsId = smsId,
            destinationAddress = phone,
            messageText = message
        )

        return when (sendResult) {
            is SmsSendResult.Success -> {
                val updated = khataSmsDao.getSmsById(smsId) ?: entity
                Result.success(updated)
            }
            is SmsSendResult.Failure -> {
                khataSmsDao.updateSentStatus(
                    id = smsId,
                    status = "FAILED",
                    errorCode = sendResult.errorCode,
                    errorMessage = sendResult.reason
                )
                Result.failure(Exception(sendResult.reason))
            }
        }
    }
}

