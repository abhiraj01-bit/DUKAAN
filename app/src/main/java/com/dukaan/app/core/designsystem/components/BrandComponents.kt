package com.dukaan.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.app.core.designsystem.theme.*

// =================== DUKAANAI BRAND LOGO ===================

@Composable
fun DukaanLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    fontSize: TextUnit = 26.sp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Logo Store Box
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(8.dp))
                .background(ForestGreen)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Roof
                val roofPath = Path().apply {
                    moveTo(w * 0.15f, h * 0.45f)
                    lineTo(w * 0.85f, h * 0.45f)
                    lineTo(w * 0.75f, h * 0.2f)
                    lineTo(w * 0.25f, h * 0.2f)
                    close()
                }
                drawPath(roofPath, color = SaffronOrange)
                // Base
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.2f, h * 0.45f),
                    size = Size(w * 0.6f, h * 0.45f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // Door/Counter
                drawRoundRect(
                    color = ForestGreen,
                    topLeft = Offset(w * 0.35f, h * 0.55f),
                    size = Size(w * 0.3f, h * 0.35f),
                    cornerRadius = CornerRadius(1f, 1f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dukaan",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    letterSpacing = 0.5.sp
                ),
                color = ForestGreen
            )
            Text(
                text = "AI",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    letterSpacing = 0.5.sp
                ),
                color = SaffronOrange
            )
        }
    }
}

// =================== SCREEN 01: INDIAN SHOPKEEPER ILLUSTRATION ===================

@Composable
fun ShopkeeperIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background Grocery Shelves
            val shelfY = listOf(h * 0.15f, h * 0.35f, h * 0.55f)
            shelfY.forEach { y ->
                // Wooden shelf plank
                drawRoundRect(
                    color = Color(0xFFD2B48C),
                    topLeft = Offset(w * 0.05f, y),
                    size = Size(w * 0.9f, 8f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Items on shelf
                var x = w * 0.08f
                while (x < w * 0.9f) {
                    val colors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFF64B5F6), Color(0xFFBA68C8))
                    val itemColor = colors[((x / 30).toInt()) % colors.size]
                    drawRoundRect(
                        color = itemColor,
                        topLeft = Offset(x, y - 28f),
                        size = Size(20f, 28f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                    x += 26f
                }
            }

            // Shop Counter Table
            drawRoundRect(
                color = Color(0xFF8D6E63),
                topLeft = Offset(w * 0.1f, h * 0.72f),
                size = Size(w * 0.8f, h * 0.28f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // Shopkeeper Torso (Green Kurta)
            drawRoundRect(
                color = ForestGreen,
                topLeft = Offset(w * 0.32f, h * 0.48f),
                size = Size(w * 0.36f, h * 0.35f),
                cornerRadius = CornerRadius(24f, 24f)
            )

            // Crossed Arms / Apron
            drawRoundRect(
                color = SaffronOrange,
                topLeft = Offset(w * 0.36f, h * 0.58f),
                size = Size(w * 0.28f, h * 0.2f),
                cornerRadius = CornerRadius(16f, 16f)
            )

            // Neck & Head
            drawCircle(
                color = Color(0xFFD7A177),
                radius = w * 0.12f,
                center = Offset(w * 0.5f, h * 0.38f)
            )

            // Hair
            drawArc(
                color = Color(0xFF2C241D),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.37f, h * 0.24f),
                size = Size(w * 0.26f, w * 0.24f)
            )

            // Mustache & Smile
            drawArc(
                color = Color(0xFF2C241D),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.44f, h * 0.40f),
                size = Size(w * 0.12f, 16f),
                style = Stroke(width = 4f)
            )
        }
    }
}

// =================== SCREEN 02: INVOICE SCANNING ILLUSTRATION ===================

@Composable
fun InvoiceScanIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Paper Invoice in background
            drawRoundRect(
                color = Color(0xFFF5F5F0),
                topLeft = Offset(w * 0.2f, h * 0.1f),
                size = Size(w * 0.6f, h * 0.75f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            // Invoice Lines
            listOf(0.2f, 0.28f, 0.36f, 0.44f, 0.52f, 0.6f, 0.68f).forEach { fraction ->
                drawRoundRect(
                    color = Color(0xFFDCD8CE),
                    topLeft = Offset(w * 0.26f, h * fraction),
                    size = Size(w * 0.48f, 6f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }

            // Phone holding over bill
            drawRoundRect(
                color = Color(0xFF2C3E50),
                topLeft = Offset(w * 0.32f, h * 0.18f),
                size = Size(w * 0.36f, h * 0.65f),
                cornerRadius = CornerRadius(18f, 18f)
            )
            // Phone screen
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.34f, h * 0.22f),
                size = Size(w * 0.32f, h * 0.55f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            // Green Camera OCR Scanning Frame
            drawRoundRect(
                color = SecondaryGreen,
                topLeft = Offset(w * 0.37f, h * 0.32f),
                size = Size(w * 0.26f, h * 0.32f),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 3f)
            )

            // Laser scan beam line
            drawLine(
                color = SaffronOrange,
                start = Offset(w * 0.36f, h * 0.46f),
                end = Offset(w * 0.64f, h * 0.46f),
                strokeWidth = 4f
            )
        }
    }
}

// =================== SCREEN 03: INVENTORY SHELVES ILLUSTRATION ===================

@Composable
fun InventoryShelvesIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 3-Tier Wooden Store Rack
            val racks = listOf(h * 0.32f, h * 0.62f, h * 0.92f)
            racks.forEach { y ->
                drawRoundRect(
                    color = Color(0xFF8D6E63),
                    topLeft = Offset(w * 0.15f, y),
                    size = Size(w * 0.7f, 10f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            // Top rack items (Jars & bottles)
            drawRoundRect(color = Color(0xFFF3A342), topLeft = Offset(w * 0.22f, h * 0.16f), size = Size(28f, 42f), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(color = Color(0xFF174D36), topLeft = Offset(w * 0.36f, h * 0.14f), size = Size(24f, 48f), cornerRadius = CornerRadius(4f, 4f))
            drawRoundRect(color = Color(0xFFE57373), topLeft = Offset(w * 0.50f, h * 0.18f), size = Size(32f, 36f), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(color = Color(0xFF4FC3F7), topLeft = Offset(w * 0.66f, h * 0.15f), size = Size(26f, 44f), cornerRadius = CornerRadius(4f, 4f))

            // Middle rack items (Atta bags & packets)
            drawRoundRect(color = Color(0xFFE53935), topLeft = Offset(w * 0.24f, h * 0.44f), size = Size(46f, 50f), cornerRadius = CornerRadius(8f, 8f))
            drawRoundRect(color = Color(0xFFFFB300), topLeft = Offset(w * 0.44f, h * 0.42f), size = Size(38f, 54f), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(color = Color(0xFF43A047), topLeft = Offset(w * 0.60f, h * 0.46f), size = Size(42f, 46f), cornerRadius = CornerRadius(8f, 8f))

            // Bottom rack items (Rice sacks & crates)
            drawRoundRect(color = Color(0xFFD7CCC8), topLeft = Offset(w * 0.22f, h * 0.74f), size = Size(60f, 52f), cornerRadius = CornerRadius(10f, 10f))
            drawRoundRect(color = Color(0xFFFFCC80), topLeft = Offset(w * 0.48f, h * 0.72f), size = Size(66f, 56f), cornerRadius = CornerRadius(10f, 10f))
        }
    }
}

// =================== SCREEN 04: KHATA DISCUSSION ILLUSTRATION ===================

@Composable
fun KhataInteractionIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Person 1 (Shopkeeper on left)
            drawCircle(color = Color(0xFFD7A177), radius = 28f, center = Offset(w * 0.32f, h * 0.28f))
            drawRoundRect(color = ForestGreen, topLeft = Offset(w * 0.22f, h * 0.42f), size = Size(w * 0.22f, h * 0.45f), cornerRadius = CornerRadius(16f, 16f))

            // Person 2 (Customer on right)
            drawCircle(color = Color(0xFFE0BB95), radius = 28f, center = Offset(w * 0.68f, h * 0.28f))
            drawRoundRect(color = Color(0xFF2E7D32), topLeft = Offset(w * 0.56f, h * 0.42f), size = Size(w * 0.22f, h * 0.45f), cornerRadius = CornerRadius(16f, 16f))

            // Exchanging a package / Khata book
            drawRoundRect(color = SaffronOrange, topLeft = Offset(w * 0.42f, h * 0.50f), size = Size(w * 0.16f, 46f), cornerRadius = CornerRadius(8f, 8f))
            // Rupee mark on book
            drawLine(color = Color.White, start = Offset(w * 0.47f, h * 0.55f), end = Offset(w * 0.53f, h * 0.55f), strokeWidth = 3f)
        }
    }
}

// =================== SCREEN 05: ANALYTICS GROWTH CHART ILLUSTRATION ===================

@Composable
fun AnalyticsChartIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 4 Growth Bars
            val bars = listOf(
                Pair(w * 0.24f, 0.45f),
                Pair(w * 0.38f, 0.60f),
                Pair(w * 0.52f, 0.75f),
                Pair(w * 0.66f, 0.95f)
            )

            bars.forEachIndexed { i, (x, heightFraction) ->
                val barH = h * 0.6f * heightFraction
                drawRoundRect(
                    color = if (i % 2 == 0) ForestGreen else SaffronOrange,
                    topLeft = Offset(x, h * 0.85f - barH),
                    size = Size(36f, barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }

            // Upward Growth Trend Arrow
            val arrowPath = Path().apply {
                moveTo(w * 0.2f, h * 0.65f)
                quadraticTo(w * 0.45f, h * 0.45f, w * 0.78f, h * 0.18f)
            }
            drawPath(arrowPath, color = SecondaryGreen, style = Stroke(width = 6f))

            // Arrow head
            val head = Path().apply {
                moveTo(w * 0.78f, h * 0.18f)
                lineTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.76f, h * 0.26f)
                close()
            }
            drawPath(head, color = SecondaryGreen)
        }
    }
}

// =================== PRODUCT IMAGE THUMBNAIL RENDERER ===================

@Composable
fun GroceryProductThumbnail(
    productName: String,
    modifier: Modifier = Modifier.size(46.dp)
) {
    val bgColor = when {
        productName.contains("Atta", ignoreCase = true) -> Color(0xFFFFEBEE)
        productName.contains("Oil", ignoreCase = true) -> Color(0xFFFFF8E1)
        productName.contains("Salt", ignoreCase = true) -> Color(0xFFE1F5FE)
        productName.contains("Milk", ignoreCase = true) -> Color(0xFFE8F5E9)
        productName.contains("Bread", ignoreCase = true) -> Color(0xFFFFF3E0)
        productName.contains("Noodle", ignoreCase = true) || productName.contains("Maggi", ignoreCase = true) -> Color(0xFFFFFDE7)
        else -> Color(0xFFF5F5F0)
    }

    val iconColor = when {
        productName.contains("Atta", ignoreCase = true) -> Color(0xFFD32F2F)
        productName.contains("Oil", ignoreCase = true) -> Color(0xFFFFA000)
        productName.contains("Salt", ignoreCase = true) -> Color(0xFF0288D1)
        productName.contains("Milk", ignoreCase = true) -> Color(0xFF388E3C)
        productName.contains("Bread", ignoreCase = true) -> Color(0xFFE64A19)
        else -> ForestGreen
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val icon = when {
            productName.contains("Oil", ignoreCase = true) -> Icons.Default.WaterDrop
            productName.contains("Milk", ignoreCase = true) -> Icons.Default.LocalDrink
            productName.contains("Bread", ignoreCase = true) -> Icons.Default.BakeryDining
            productName.contains("Noodle", ignoreCase = true) -> Icons.Default.RamenDining
            productName.contains("Salt", ignoreCase = true) -> Icons.Default.Grain
            else -> Icons.Default.ShoppingBag
        }
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
    }
}