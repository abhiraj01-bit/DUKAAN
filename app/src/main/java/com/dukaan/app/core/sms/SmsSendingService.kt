package com.dukaan.app.core.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.dukaan.app.core.datastore.DukaanPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed class SmsSendResult {
    data class Success(val smsId: String) : SmsSendResult()
    data class Failure(val smsId: String, val reason: String, val errorCode: Int? = null) : SmsSendResult()
}

data class SimCardInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
)

@Singleton
class SmsSendingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DukaanPreferences
) {
    companion object {
        private const val TAG = "SmsSendingService"
        private const val SMS_SENT_TIMEOUT_MS = 20_000L
    }

    /**
     * Checks if SEND_SMS permission is granted.
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns available SIM cards on dual-SIM / multi-SIM devices.
     */
    @SuppressLint("MissingPermission")
    fun getAvailableSimCards(): List<SimCardInfo> {
        val list = mutableListOf<SimCardInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return list
        }
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subList: List<SubscriptionInfo>? = subManager?.activeSubscriptionInfoList
            subList?.forEach { sub ->
                list.add(
                    SimCardInfo(
                        subscriptionId = sub.subscriptionId,
                        slotIndex = sub.simSlotIndex,
                        displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                        carrierName = sub.carrierName?.toString() ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying dual-SIM subscriptions: ${e.message}")
        }
        return list
    }

    /**
     * Directly sends an SMS through the device's physical SIM card via native SmsManager.
     * Never opens an external SMS app, WhatsApp, or composer.
     */
    suspend fun sendDirectSms(
        smsId: String,
        destinationAddress: String,
        messageText: String,
        preferredSubId: Int? = null
    ): SmsSendResult = withContext(Dispatchers.IO) {

        if (!hasSmsPermission()) {
            Log.e(TAG, "SEND_SMS permission not granted")
            return@withContext SmsSendResult.Failure(smsId, "SEND_SMS permission not granted on device")
        }

        val cleanPhone = cleanPhoneNumber(destinationAddress)
        if (cleanPhone.length < 10) {
            Log.e(TAG, "Invalid phone number: $destinationAddress")
            return@withContext SmsSendResult.Failure(smsId, "Invalid customer phone number ($destinationAddress)")
        }

        val subIdToUse = preferredSubId ?: preferences.preferredSimSubId.first().let { if (it >= 0) it else null }

        val smsManager = try {
            resolveSmsManager(subIdToUse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve SmsManager: ${e.message}")
            return@withContext SmsSendResult.Failure(smsId, "Could not access device SMS service: ${e.message}")
        }

        val sentIntent = PendingIntent.getBroadcast(
            context,
            smsId.hashCode(),
            Intent(SmsDeliveryReceiver.ACTION_SMS_SENT).apply {
                setPackage(context.packageName)
                putExtra(SmsDeliveryReceiver.EXTRA_SMS_ID, smsId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveryIntent = PendingIntent.getBroadcast(
            context,
            smsId.hashCode() + 1,
            Intent(SmsDeliveryReceiver.ACTION_SMS_DELIVERED).apply {
                setPackage(context.packageName)
                putExtra(SmsDeliveryReceiver.EXTRA_SMS_ID, smsId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Split message if text exceeds single SMS character limit (multi-part SMS)
        val parts = smsManager.divideMessage(messageText)
        val numParts = parts.size

        val sentIntents = ArrayList<PendingIntent>()
        val deliveryIntents = ArrayList<PendingIntent>()
        for (i in 0 until numParts) {
            // Only attach callback to the last part or all parts
            sentIntents.add(sentIntent)
            deliveryIntents.add(deliveryIntent)
        }

        Log.d(TAG, "Dispatching direct SIM SMS for smsId=$smsId to $cleanPhone (${parts.size} parts, subId=$subIdToUse)")

        try {
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    cleanPhone,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    cleanPhone,
                    null,
                    messageText,
                    sentIntent,
                    deliveryIntent
                )
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException sending SMS: ${se.message}")
            return@withContext SmsSendResult.Failure(smsId, "SMS Permission revoked by system")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in SmsManager.sendTextMessage: ${e.message}")
            return@withContext SmsSendResult.Failure(smsId, "Failed to send SMS: ${e.localizedMessage}")
        }

        // Wait for actual native Android SMS_SENT broadcast callback
        val sentEvent = withTimeoutOrNull(SMS_SENT_TIMEOUT_MS) {
            SmsDeliveryReceiver.events
                .filter { it.smsId == smsId && it is SmsDeliveryReceiver.SmsEvent.Sent }
                .first() as? SmsDeliveryReceiver.SmsEvent.Sent
        }

        if (sentEvent == null) {
            Log.w(TAG, "Timeout waiting for SMS_SENT callback for smsId=$smsId")
            // Submitted to system buffer, status remains PENDING in Room until broadcast delivers
            return@withContext SmsSendResult.Success(smsId)
        }

        if (sentEvent.isSuccess) {
            Log.d(TAG, "SMS successfully sent via SIM for smsId=$smsId")
            SmsSendResult.Success(smsId)
        } else {
            Log.w(TAG, "SMS sending failed: ${sentEvent.errorReason} (code: ${sentEvent.resultCode})")
            SmsSendResult.Failure(
                smsId = smsId,
                reason = sentEvent.errorReason ?: "Carrier rejected SMS transmission",
                errorCode = sentEvent.resultCode
            )
        }
    }

    private fun resolveSmsManager(subId: Int?): SmsManager {
        return if (subId != null && subId >= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = context.getSystemService(SmsManager::class.java)
                base.createForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    private fun cleanPhoneNumber(raw: String): String {
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
}

