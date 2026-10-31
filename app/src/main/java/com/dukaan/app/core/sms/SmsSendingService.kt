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
        private const val SMS_SENT_TIMEOUT_MS = 15_000L
    }

    /**
     * Checks if SEND_SMS permission is granted.
     */
    fun hasSmsPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SEND_SMS permission: ${e.message}", e)
            false
        }
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
        try {
            if (!hasSmsPermission()) {
                Log.e(TAG, "SEND_SMS permission not granted on device")
                return@withContext SmsSendResult.Failure(smsId, "SEND_SMS permission not granted. Please allow SMS permission in app settings.")
            }

            val cleanPhone = cleanPhoneNumber(destinationAddress)
            if (cleanPhone.length < 10) {
                Log.e(TAG, "Invalid customer phone number length: ${cleanPhone.length}")
                return@withContext SmsSendResult.Failure(smsId, "Invalid customer mobile number ($destinationAddress). Must be a valid 10-digit number.")
            }

            val subIdToUse = try {
                preferredSubId ?: preferences.preferredSimSubId.first().let { if (it >= 0) it else null }
            } catch (e: Exception) {
                null
            }

            val smsManager = resolveSmsManager(subIdToUse)
            if (smsManager == null) {
                Log.e(TAG, "Could not resolve an SmsManager instance on this device")
                return@withContext SmsSendResult.Failure(smsId, "Cellular SMS service is unavailable on this device. Please check SIM card.")
            }

            // PendingIntent flags: On Android 12+ (API 31+), telephony callbacks require FLAG_MUTABLE so the system can fill in result data
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentReqCode = smsId.hashCode() and 0x7FFFFFFF
            val deliveryReqCode = (smsId.hashCode() + 31) and 0x7FFFFFFF

            val sentIntent = try {
                PendingIntent.getBroadcast(
                    context,
                    sentReqCode,
                    Intent(context, SmsDeliveryReceiver::class.java).apply {
                        action = SmsDeliveryReceiver.ACTION_SMS_SENT
                        putExtra(SmsDeliveryReceiver.EXTRA_SMS_ID, smsId)
                    },
                    pendingIntentFlags
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating sent PendingIntent: ${e.message}", e)
                null
            }

            val deliveryIntent = try {
                PendingIntent.getBroadcast(
                    context,
                    deliveryReqCode,
                    Intent(context, SmsDeliveryReceiver::class.java).apply {
                        action = SmsDeliveryReceiver.ACTION_SMS_DELIVERED
                        putExtra(SmsDeliveryReceiver.EXTRA_SMS_ID, smsId)
                    },
                    pendingIntentFlags
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating delivery PendingIntent: ${e.message}", e)
                null
            }

            // Split message if text exceeds single SMS character limit (multi-part SMS)
            val parts = try {
                smsManager.divideMessage(messageText)
            } catch (e: Exception) {
                Log.w(TAG, "divideMessage threw exception, falling back to single text: ${e.message}")
                arrayListOf(messageText)
            }

            if (parts.isNullOrEmpty()) {
                return@withContext SmsSendResult.Failure(smsId, "Message text could not be formatted for SMS.")
            }

            val numParts = parts.size
            val maskedPhone = if (cleanPhone.length >= 4) "******" + cleanPhone.takeLast(4) else "***"
            Log.d(TAG, "Dispatching direct SIM SMS for smsId=$smsId to $maskedPhone (parts=$numParts, subId=$subIdToUse)")

            try {
                if (numParts > 1) {
                    val sentIntents = ArrayList<PendingIntent?>()
                    val deliveryIntents = ArrayList<PendingIntent?>()
                    for (i in 0 until numParts) {
                        sentIntents.add(sentIntent)
                        deliveryIntents.add(deliveryIntent)
                    }
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
                Log.e(TAG, "SecurityException sending SMS: ${se.message}", se)
                return@withContext SmsSendResult.Failure(smsId, "SMS permission was revoked or restricted by system.")
            } catch (iae: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException sending SMS: ${iae.message}", iae)
                return@withContext SmsSendResult.Failure(smsId, "Invalid mobile number or message format: ${iae.message}")
            } catch (uoe: UnsupportedOperationException) {
                Log.e(TAG, "UnsupportedOperationException sending SMS: ${uoe.message}", uoe)
                return@withContext SmsSendResult.Failure(smsId, "Device or carrier does not support direct SIM SMS.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in SmsManager dispatch: ${e.message}", e)
                return@withContext SmsSendResult.Failure(smsId, "Failed to send SMS: ${e.localizedMessage ?: "Unknown error"}")
            }

            // Wait for native Android SMS_SENT broadcast callback with safe timeout
            val sentEvent = withTimeoutOrNull(SMS_SENT_TIMEOUT_MS) {
                SmsDeliveryReceiver.events
                    .filter { it.smsId == smsId && it is SmsDeliveryReceiver.SmsEvent.Sent }
                    .first() as? SmsDeliveryReceiver.SmsEvent.Sent
            }

            if (sentEvent == null) {
                Log.w(TAG, "Timeout waiting for SMS_SENT callback for smsId=$smsId, submitted to carrier buffer")
                return@withContext SmsSendResult.Success(smsId)
            }

            if (sentEvent.isSuccess) {
                Log.d(TAG, "SMS confirmed sent by carrier for smsId=$smsId")
                SmsSendResult.Success(smsId)
            } else {
                Log.w(TAG, "SMS sending reported failed by carrier: ${sentEvent.errorReason} (code: ${sentEvent.resultCode})")
                SmsSendResult.Failure(
                    smsId = smsId,
                    reason = sentEvent.errorReason ?: "Carrier rejected SMS transmission",
                    errorCode = sentEvent.resultCode
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Critical error in sendDirectSms: ${t.message}", t)
            SmsSendResult.Failure(smsId, "SMS dispatch error: ${t.localizedMessage ?: "Unexpected error"}")
        }
    }

    /**
     * Resolves the proper SmsManager instance across Android 7 to Android 15+.
     * Validates subscription ID and falls back gracefully to default SmsManager.
     */
    private fun resolveSmsManager(subId: Int?): SmsManager? {
        // 1. Try subscription-specific SmsManager if requested
        if (subId != null && subId >= 0) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val base = context.getSystemService(SmsManager::class.java)
                    if (base != null) {
                        return base.createForSubscriptionId(subId)
                    }
                }
                @Suppress("DEPRECATION")
                val subMgr = SmsManager.getSmsManagerForSubscriptionId(subId)
                if (subMgr != null) return subMgr
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving SmsManager for subId $subId: ${e.message}, falling back to default")
            }
        }

        // 2. Check default SMS subscription if dual SIM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
                if (defaultSubId > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val base = context.getSystemService(SmsManager::class.java)
                        if (base != null) {
                            return base.createForSubscriptionId(defaultSubId)
                        }
                    }
                    @Suppress("DEPRECATION")
                    val subMgr = SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
                    if (subMgr != null) return subMgr
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed checking defaultSmsSubscriptionId: ${e.message}")
            }
        }

        // 3. Try system service SmsManager (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val sysMgr = context.getSystemService(SmsManager::class.java)
                if (sysMgr != null) return sysMgr
            } catch (e: Exception) {
                Log.w(TAG, "getSystemService(SmsManager) failed: ${e.message}")
            }
        }

        // 4. Fallback to SmsManager.getDefault()
        return try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } catch (e: Exception) {
            Log.e(TAG, "SmsManager.getDefault() failed: ${e.message}", e)
            null
        }
    }

    /**
     * Cleans phone numbers for domestic SMS dispatch.
     * Indian mobile numbers are normalized to standard 10 digits for local SMSC routing.
     */
    fun cleanPhoneNumber(raw: String): String {
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
}
