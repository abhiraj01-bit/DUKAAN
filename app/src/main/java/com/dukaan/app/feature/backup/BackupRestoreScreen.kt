package com.dukaan.app.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dukaan.app.core.database.DukaanDatabase
import com.dukaan.app.core.datastore.DukaanPreferences
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.util.DateFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val preferences: DukaanPreferences
) : ViewModel() {
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    fun createLocalBackup(context: Context) {
        viewModelScope.launch {
            _isBackingUp.value = true
            withContext(Dispatchers.IO) {
                try {
                    val dbFile = context.getDatabasePath(DukaanDatabase.DATABASE_NAME)
                    val backupFile = File(context.cacheDir, "Dukaan_Backup_${System.currentTimeMillis()}.db")

                    if (dbFile.exists()) {
                        FileInputStream(dbFile).use { input ->
                            FileOutputStream(backupFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        preferences.setLastBackupTime(System.currentTimeMillis())

                        withContext(Dispatchers.Main) {
                            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Save / Share Backup File"))
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    _isBackingUp.value = false
                }
            }
        }
    }
}

@Composable
fun BackupRestoreScreen(
    navController: NavController,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isBackingUp by viewModel.isBackingUp.collectAsState()

    Scaffold(
        topBar = {
            DukaanTopBar(
                title = "Backup & Restore",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = WarmCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("100% Offline & Safe", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ForestGreen)
                    }

                    Text(
                        "All your business records (products, stock movements, invoices, customer khata) reside securely on your device. You can create an offline backup anytime and save it to Google Drive, WhatsApp, or an SD card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            PrimaryButton(
                text = "Export Offline Backup File",
                onClick = { viewModel.createLocalBackup(context) },
                loading = isBackingUp,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.CloudUpload
            )
        }
    }
}