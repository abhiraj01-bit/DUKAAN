package com.dukaan.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local Room entity storing all outgoing SIM-based SMS reminder attempts and delivery callbacks.
 */
@Entity(
    tableName = "khata_sms_reminders",
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["unique_key"], unique = true),
        Index(value = ["created_at"])
    ]
)
data class KhataSmsEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "balance_paise")
    val balancePaise: Long,

    /**
     * "MANUAL" for button clicks, "MONTHLY_AUTO" for month-end batch.
     */
    @ColumnInfo(name = "reminder_type")
    val reminderType: String,

    /**
     * Format: YYYY-MM (e.g. "2026-10") to avoid duplicate batch sends in the same month.
     */
    @ColumnInfo(name = "month_year")
    val monthYear: String,

    /**
     * "PENDING", "SENT", "DELIVERED", "FAILED"
     */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sent_at")
    val sentAt: Long? = null,

    @ColumnInfo(name = "delivered_at")
    val deliveredAt: Long? = null,

    @ColumnInfo(name = "error_code")
    val errorCode: Int? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    /**
     * Unique key preventing duplicate sending.
     * Manual: "manual_${id}"
     * Auto: "monthly_${customerId}_${monthYear}"
     */
    @ColumnInfo(name = "unique_key")
    val uniqueKey: String
)

