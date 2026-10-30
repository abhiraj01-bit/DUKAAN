package com.dukaan.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dukaan.app.core.designsystem.components.PrimaryButton
import com.dukaan.app.core.designsystem.components.SecondaryButton
import com.dukaan.app.core.designsystem.theme.*

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Smart Inventory & Stock",
                subtitle = "Keep real-time counts of grocery, packaged goods, and daily retail items. Get low stock alerts before you run out.",
                icon = Icons.Default.Inventory2,
                badge = "STEP 1 OF 3"
            ),
            OnboardingStep(
                title = "Camera Invoice Scanning",
                subtitle = "Point your camera at wholesaler invoices. Our on-device OCR extracts items, prices, and quantities automatically.",
                icon = Icons.Default.DocumentScanner,
                badge = "STEP 2 OF 3"
            ),
            OnboardingStep(
                title = "Digital Khata & Udhar",
                subtitle = "Replace paper bahi-khata. Record customer balances, send WhatsApp payment reminders, and keep clean audit logs.",
                icon = Icons.Default.AccountBalanceWallet,
                badge = "STEP 3 OF 3"
            )
        )
    }

    var currentStep by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar / Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = steps[currentStep].badge,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ForestGreen
                )
                if (currentStep < steps.size - 1) {
                    TextButton(onClick = onComplete) {
                        Text("Skip", color = SecondaryText)
                    }
                }
            }

            // Slide Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(SuccessGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = steps[currentStep].icon,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = steps[currentStep].title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Charcoal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = steps[currentStep].subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(steps.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentStep) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (index == currentStep) ForestGreen else DividerColor)
                        )
                    }
                }
            }

            // Bottom Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 0) {
                    SecondaryButton(
                        text = "Back",
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f)
                    )
                }

                PrimaryButton(
                    text = if (currentStep == steps.size - 1) "Start Shop Setup" else "Next",
                    onClick = {
                        if (currentStep == steps.size - 1) {
                            onComplete()
                        } else {
                            currentStep++
                        }
                    },
                    modifier = Modifier.weight(if (currentStep > 0) 1.5f else 1f)
                )
            }
        }
    }
}