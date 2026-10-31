package com.dukaan.app.core.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.dukaan.app.data.local.dao.KhataSmsDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Global broadcast receiver for native Android SmsManager sent & delivered callbacks.
 */
class SmsDeliveryReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun khataSmsDao(): KhataSmsDao
    }

    companion object {
        private const val TAG = "SmsDeliveryReceiver"
        const val ACTION_SMS_SENT = "com.dukaan.app.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.dukaan.app.SMS_DELIVERED"
        const val EXTRA_SMS_ID = "extra_sms_id"

        private val _events = MutableSharedFlow<SmsEvent>(extraBufferCapacity = 64)
        val events: SharedFlow<SmsEvent> = _events.asSharedFlow()
    }

    sealed class SmsEvent(val smsId: String) {
        class Sent(smsId: String, val resultCode: Int, val isSuccess: Boolean, val errorReason: String?) : SmsEvent(smsId)
        class Delivered(smsId: String, val isDelivered: Boolean) : SmsEvent(smsId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val smsId = intent.getStringExtra(EXTRA_SMS_ID) ?: return
            val action = intent.action ?: return
            val resultCode = resultCode
            val radioErrorCode = intent.getIntExtra("errorCode", -1)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entryPoint = try {
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            SmsReceiverEntryPoint::class.java
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resolve Hilt entry point: ${e.message}")
                        null
                    }
                    val khataSmsDao = entryPoint?.khataSmsDao()

                    when (action) {
                        ACTION_SMS_SENT -> {
                            val isSuccess = resultCode == Activity.RESULT_OK
                            val (status, errorMsg) = when (resultCode) {
                                Activity.RESULT_OK -> "SENT" to null
                                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                                    val detail = when (radioErrorCode) {
                                        1 -> "Invalid/unassigned recipient number. Please verify customer phone number."
                                        21 -> "Carrier rejected SMS: Check if your SIM has an active SMS pack/main balance."
                                        38 -> "Network out of order: Carrier temporary error. Please retry."
                                        27, 28 -> "Destination unreachable or out of service."
                                        else -> if (radioErrorCode > 0) {
                                            "Carrier rejected SMS (Radio error: $radioErrorCode). Ensure SIM has active SMS balance."
                                        } else {
                                            "Carrier generic failure. Common causes: SIM has no active SMS pack/balance, or invalid recipient number."
                                        }
                                    }
                                    "FAILED" to detail
                                }
                                SmsManager.RESULT_ERROR_RADIO_OFF -> "FAILED" to "Cellular radio is off / Airplane mode active."
                                SmsManager.RESULT_ERROR_NULL_PDU -> "FAILED" to "Null PDU received from carrier."
                                SmsManager.RESULT_ERROR_NO_SERVICE -> "FAILED" to "No cellular network service available on SIM."
                                SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "FAILED" to "Daily SMS sending limit exceeded on device."
                                else -> "FAILED" to "SMS error (code: $resultCode, radio: $radioErrorCode)."
                            }

                            Log.d(TAG, "SMS_SENT for smsId=$smsId, resultCode=$resultCode, radioErrorCode=$radioErrorCode, status=$status")
                            try {
                                khataSmsDao?.updateSentStatus(
                                    id = smsId,
                                    status = status,
                                    sentAt = if (isSuccess) System.currentTimeMillis() else null,
                                    errorCode = if (!isSuccess) (if (radioErrorCode > 0) radioErrorCode else resultCode) else null,
                                    errorMessage = errorMsg
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed updating sent status in DB: ${e.message}")
                            }
                            _events.emit(SmsEvent.Sent(smsId, resultCode, isSuccess, errorMsg))
                        }

                        ACTION_SMS_DELIVERED -> {
                            val isDelivered = resultCode == Activity.RESULT_OK
                            Log.d(TAG, "SMS_DELIVERED for smsId=$smsId, isDelivered=$isDelivered")
                            try {
                                if (isDelivered) {
                                    khataSmsDao?.updateDeliveredStatus(id = smsId, status = "DELIVERED")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed updating delivered status in DB: ${e.message}")
                            }
                            _events.emit(SmsEvent.Delivered(smsId, isDelivered))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in SmsDeliveryReceiver async processing: ${e.message}", e)
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (_: Exception) {}
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception in SmsDeliveryReceiver.onReceive: ${t.message}", t)
        }
    }
}
