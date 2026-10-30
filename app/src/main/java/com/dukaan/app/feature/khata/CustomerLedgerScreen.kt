package com.dukaan.app.feature.khata

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.util.CurrencyFormatter
import com.dukaan.app.core.util.DateFormatter
import com.dukaan.app.domain.model.Customer
import com.dukaan.app.domain.model.KhataTransaction
import com.dukaan.app.domain.model.KhataTransactionType
import com.dukaan.app.feature.customers.CustomerDetailViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun CustomerLedgerScreen(
    navController: NavController,
    customerId: String,
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(customerId) {
        viewModel.loadCustomer(customerId)
    }

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "${state.customer?.name ?: "Customer"} Ledger",
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = {
                        exportLedgerPdf(context, state.customer, state.transactions)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = SurfaceWhite)
                    }
                }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(paddingValues))
        } else if (state.customer == null) {
            ErrorState(message = "Customer not found", onRetry = { navController.popBackStack() })
        } else {
            val customer = state.customer!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Summary Card
                Surface(
                    color = SurfaceWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NET OUTSTANDING", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Text(
                                CurrencyFormatter.formatPaise(customer.outstandingPaise),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (customer.outstandingPaise > 0) ErrorRed else SuccessGreen
                            )
                        }

                        Button(
                            onClick = {
                                exportLedgerPdf(context, customer, state.transactions)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF Statement")
                        }
                    }
                }

                if (state.transactions.isEmpty()) {
                    EmptyState(
                        title = "No khata entries yet",
                        subtitle = "Recorded credits and payments will appear in this ledger.",
                        icon = Icons.Default.Book
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.transactions) { txn ->
                            val isCredit = txn.type == KhataTransactionType.CREDIT

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isCredit) "Gave Credit (+ Udhar)" else "Payment Received (- Jama)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isCredit) ErrorRed else SuccessGreen
                                        )
                                        Text(
                                            DateFormatter.formatFull(txn.createdAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SecondaryText
                                        )
                                        if (!txn.notes.isNullOrBlank()) {
                                            Text(
                                                txn.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Charcoal
                                            )
                                        }
                                    }

                                    Text(
                                        "${if (isCredit) "+" else "-"} ${CurrencyFormatter.formatPaise(txn.amountPaise)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isCredit) ErrorRed else SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportLedgerPdf(context: Context, customer: Customer?, transactions: List<KhataTransaction>) {
    if (customer == null) return
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Title
        paint.color = AndroidColor.rgb(23, 77, 54) // Forest Green
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("DUKAAN — Customer Khata Statement", 40f, 50f, paint)

        // Customer details
        paint.color = AndroidColor.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Customer Name: ${customer.name}", 40f, 80f, paint)
        canvas.drawText("Phone: ${customer.phoneNumber ?: "N/A"}", 40f, 98f, paint)
        canvas.drawText("Current Balance Due: ${CurrencyFormatter.formatPaise(customer.outstandingPaise)}", 40f, 116f, paint)

        // Divider
        paint.color = AndroidColor.LTGRAY
        canvas.drawLine(40f, 130f, 555f, 130f, paint)

        // Table Header
        paint.color = AndroidColor.DKGRAY
        paint.isFakeBoldText = true
        canvas.drawText("Date & Time", 40f, 150f, paint)
        canvas.drawText("Type", 220f, 150f, paint)
        canvas.drawText("Description", 320f, 150f, paint)
        canvas.drawText("Amount", 480f, 150f, paint)

        // Rows
        paint.isFakeBoldText = false
        var yPos = 175f
        for (txn in transactions.take(25)) {
            val dateStr = DateFormatter.formatShortDate(txn.createdAt)
            val typeStr = if (txn.type == KhataTransactionType.CREDIT) "Udhar (+)" else "Jama (-)"
            val notesStr = txn.notes?.take(18) ?: "-"
            val amtStr = CurrencyFormatter.formatPaise(txn.amountPaise)

            canvas.drawText(dateStr, 40f, yPos, paint)
            canvas.drawText(typeStr, 220f, yPos, paint)
            canvas.drawText(notesStr, 320f, yPos, paint)
            canvas.drawText(amtStr, 480f, yPos, paint)
            yPos += 22f
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "Khata_${customer.name.replace(" ", "_")}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Khata Statement"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to create PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}