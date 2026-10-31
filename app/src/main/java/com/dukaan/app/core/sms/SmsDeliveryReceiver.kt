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
        val smsId = intent.getStringExtra(EXTRA_SMS_ID) ?: return
        val action = intent.action ?: return
        val resultCode = resultCode

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsReceiverEntryPoint::class.java
        )
        val khataSmsDao = entryPoint.khataSmsDao()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_SMS_SENT -> {
                        val isSuccess = resultCode == Activity.RESULT_OK
                        val (status, errorMsg) = when (resultCode) {
                            Activity.RESULT_OK -> "SENT" to null
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "FAILED" to "Carrier generic network failure"
                            SmsManager.RESULT_ERROR_RADIO_OFF -> "FAILED" to "Cellular radio is off / Airplane mode active"
                            SmsManager.RESULT_ERROR_NULL_PDU -> "FAILED" to "Null PDU received from carrier"
                            SmsManager.RESULT_ERROR_NO_SERVICE -> "FAILED" to "No cellular network service available"
                            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "FAILED" to "SMS sending limit exceeded on device"
                            else -> "FAILED" to "SMS service error (code: $resultCode)"
                        }

                        Log.d(TAG, "SMS_SENT for smsId=$smsId, resultCode=$resultCode, status=$status")
                        khataSmsDao.updateSentStatus(
                            id = smsId,
                            status = status,
                            sentAt = if (isSuccess) System.currentTimeMillis() else null,
                            errorCode = if (!isSuccess) resultCode else null,
                            errorMessage = errorMsg
                        )
                        _events.emit(SmsEvent.Sent(smsId, resultCode, isSuccess, errorMsg))
                    }

                    ACTION_SMS_DELIVERED -> {
                        val isDelivered = resultCode == Activity.RESULT_OK
                        Log.d(TAG, "SMS_DELIVERED for smsId=$smsId, isDelivered=$isDelivered")
                        if (isDelivered) {
                            khataSmsDao.updateDeliveredStatus(id = smsId, status = "DELIVERED")
                        }
                        _events.emit(SmsEvent.Delivered(smsId, isDelivered))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating SMS status in database: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

