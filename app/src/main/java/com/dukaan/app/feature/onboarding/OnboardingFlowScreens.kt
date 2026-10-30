package com.dukaan.app.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dukaan.app.core.designsystem.components.*
import com.dukaan.app.core.designsystem.theme.*
import com.dukaan.app.core.navigation.Screen
import kotlinx.coroutines.delay

// =================== SCREEN 01: SPLASH SCREEN ===================

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: com.dukaan.app.core.navigation.AppViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val isOnboardingDone by viewModel.isOnboardingDone.collectAsState()
    val appPin by viewModel.appPin.collectAsState()

    LaunchedEffect(isOnboardingDone, appPin) {
        // Wait until we know the real value (not null/loading)
        if (isOnboardingDone == null) return@LaunchedEffect
        delay(2200)
        if (isOnboardingDone == true) {
            if (appPin.isNotBlank()) {
                navController.navigate(Screen.SetPin.createRoute(isUnlock = true)) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            } else {
                navController.navigate(Screen.HomeDashboard.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        } else {
            navController.navigate(Screen.Onboarding1.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

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
            // Top Logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                DukaanLogo(iconSize = 36.dp, fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A smarter way\nto manage your shop",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    ),
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )
            }

            // Indian Shopkeeper Illustration
            ShopkeeperIllustration(modifier = Modifier.padding(horizontal = 8.dp))

            // Bottom Hindi slogan & progress
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Sarkari kaam, hisaab kaam,\nab sab ek app me",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = PrimaryText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3 subtle loading dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ForestGreen))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SaffronOrange))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BorderColor))
                }
            }
        }
    }
}

// =================== ONBOARDING BASE CONTAINER ===================

@Composable
fun OnboardingScreenLayout(
    illustration: @Composable () -> Unit,
    title: String,
    description: String,
    currentStep: Int, // 1 to 4
    onNext: () -> Unit
) {
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
            Spacer(modifier = Modifier.height(20.dp))

            // Illustration Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                illustration()
            }

            // Texts & Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 32.sp
                    ),
                    color = PrimaryText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                if (currentStep < 4) {
                    // Pagination dots + Circular Orange Next FAB
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 4 Dots
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { step ->
                                val isActive = step == currentStep
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 10.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) ForestGreen else BorderColor)
                                )
                            }
                        }

                        // Circular Next Button (#F3A342)
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(SaffronOrange)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                tint = SurfaceWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                } else {
                    // Step 4: Full-width green "Get Started"
                    PrimaryButton(
                        text = "Get Started",
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// =================== SCREEN 02: ONBOARDING 1 ===================
@Composable
fun Onboarding1Screen(navController: NavController) {
    OnboardingScreenLayout(
        illustration = { InvoiceScanIllustration() },
        title = "Scan any bill\nHandwritten or Printed",
        description = "Convert your bills into digital records in seconds with AI",
        currentStep = 1,
        onNext = { navController.navigate(Screen.Onboarding2.route) }
    )
}

// =================== SCREEN 03: ONBOARDING 2 ===================
@Composable
fun Onboarding2Screen(navController: NavController) {
    OnboardingScreenLayout(
        illustration = { InventoryShelvesIllustration() },
        title = "Manage your stock\neasily",
        description = "Know what's in stock, get low stock alerts and never run out",
        currentStep = 2,
        onNext = { navController.navigate(Screen.Onboarding3.route) }
    )
}

// =================== SCREEN 04: ONBOARDING 3 ===================
@Composable
fun Onboarding3Screen(navController: NavController) {
    OnboardingScreenLayout(
        illustration = { KhataInteractionIllustration() },
        title = "Keep track of udhaar\n(Khata)",
        description = "Maintain customer credit and payments without a notebook",
        currentStep = 3,
        onNext = { navController.navigate(Screen.Onboarding4.route) }
    )
}

// =================== SCREEN 05: ONBOARDING 4 ===================
@Composable
fun Onboarding4Screen(navController: NavController) {
    OnboardingScreenLayout(
        illustration = { AnalyticsChartIllustration() },
        title = "Understand your\nbusiness better",
        description = "Get clear insights on sales, profit, stock and customers",
        currentStep = 4,
        onNext = { navController.navigate(Screen.LanguageSelection.route) }
    )
}

// =================== SCREEN 06: LANGUAGE SELECTION ===================

@Composable
fun LanguageSelectionScreen(navController: NavController) {
    var selectedLanguage by remember { mutableStateOf("en") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Choose Language",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryText
                )
                Text(
                    text = "अपनी भाषा चुनें",
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText
                )

                Spacer(modifier = Modifier.height(32.dp))

                // English Card (Selected state in reference)
                LanguageCard(
                    title = "English",
                    subtitle = "(Selected)",
                    iconChar = "A",
                    isSelected = selectedLanguage == "en",
                    onClick = { selectedLanguage = "en" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hindi Card
                LanguageCard(
                    title = "हिन्दी",
                    subtitle = "Hindi",
                    iconChar = "अ",
                    isSelected = selectedLanguage == "hi",
                    onClick = { selectedLanguage = "hi" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Punjabi Card
                LanguageCard(
                    title = "ਪੰਜਾਬੀ",
                    subtitle = "Punjabi",
                    iconChar = "ੳ",
                    isSelected = selectedLanguage == "pa",
                    onClick = { selectedLanguage = "pa" }
                )
            }

            PrimaryButton(
                text = "Continue",
                onClick = { navController.navigate(Screen.ShopSetup.route) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}

@Composable
private fun LanguageCard(
    title: String,
    subtitle: String,
    iconChar: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) ForestGreen else BorderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) LightGreenSurface else SurfaceWhite
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) ForestGreen else WarmCream),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconChar,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isSelected) SurfaceWhite else PrimaryText
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryText
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =================== SCREEN 07: LOGIN (MOBILE / WELCOME BACK) ===================

@Composable
fun LoginScreen(navController: NavController) {
    var phoneInput by remember { mutableStateOf("") }

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(36.dp))
                DukaanLogo(iconSize = 36.dp, fontSize = 28.sp)

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryText
                )
                Text(
                    text = "फिर से स्वागत है!",
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Indian Phone number input with +91 box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .height(56.dp)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+91",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = PrimaryText
                            )
                        }
                    }

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { if (it.length <= 10) phoneInput = it },
                        placeholder = { Text("Enter mobile number", color = SecondaryText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                PrimaryButton(
                    text = "Send OTP",
                    onClick = { navController.navigate(Screen.ShopSetup.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
            }

            // Disclaimer
            Text(
                text = "By continuing, you agree to our\nTerms & Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

// =================== SCREEN 08: SHOP SETUP ===================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceShopSetupScreen(
    navController: NavController,
    viewModel: ShopSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            navController.navigate(Screen.SetPin.route) {
                popUpTo(Screen.ShopSetup.route) { inclusive = true }
            }
        }
    }

    val businessTypes = listOf(
        "Kirana / Grocery Store",
        "General / Retail Store",
        "Medical / Pharmacy",
        "Clothing / Garments",
        "Hardware / Electrical",
        "Fruits & Vegetables",
        "Wholesale Trader",
        "Other"
    )
    var expandedTypeDropdown by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Set up your Shop",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryText
                )
                Text(
                    text = "अपनी दुकान का विवरण दें",
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Shop photo upload box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LightGreenSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(38.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Shop Photo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PrimaryText)
                            Text("Tap to add photo", style = MaterialTheme.typography.bodySmall, color = SecondaryGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Shop Name — real field
                OutlinedTextField(
                    value = state.shopName,
                    onValueChange = viewModel::onShopNameChange,
                    label = { Text("Shop Name (दुकान का नाम) *") },
                    placeholder = { Text("e.g. Sharma General Store", color = SecondaryText) },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = ForestGreen) },
                    isError = state.error != null && state.shopName.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Owner Name
                OutlinedTextField(
                    value = state.ownerName,
                    onValueChange = viewModel::onOwnerNameChange,
                    label = { Text("Owner Name (मालिक का नाम)") },
                    placeholder = { Text("e.g. Ramesh Gupta", color = SecondaryText) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Business Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedTypeDropdown,
                    onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown }
                ) {
                    OutlinedTextField(
                        value = state.businessType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Business Type (व्यापार का प्रकार)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = ForestGreen) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypeDropdown,
                        onDismissRequest = { expandedTypeDropdown = false }
                    ) {
                        businessTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    viewModel.onBusinessTypeChange(type)
                                    expandedTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Phone Number
                OutlinedTextField(
                    value = state.phoneNumber,
                    onValueChange = viewModel::onPhoneNumberChange,
                    label = { Text("Mobile Number (फ़ोन नंबर)") },
                    placeholder = { Text("e.g. 9876543210", color = SecondaryText) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = ForestGreen) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Address
                OutlinedTextField(
                    value = state.address,
                    onValueChange = viewModel::onAddressChange,
                    label = { Text("Address (पता)") },
                    placeholder = { Text("e.g. Main Market, Sector 21", color = SecondaryText) },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = ForestGreen) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    )
                )

                // Error
                if (state.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }

            PrimaryButton(
                text = if (state.isLoading) "Saving..." else "Continue",
                onClick = { viewModel.completeSetup() },
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}

// =================== SCREEN 09: SET / UNLOCK APP PIN ===================

@Composable
fun SetPinScreen(
    navController: NavController,
    isUnlockMode: Boolean = false
) {
    val viewModel: SetPinViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val savedPin by viewModel.savedPin.collectAsState(initial = "")
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            if (isUnlockMode) {
                if (enteredPin == savedPin || savedPin.isBlank()) {
                    delay(200)
                    navController.navigate(Screen.HomeDashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    delay(300)
                    errorMessage = "Incorrect PIN. Try again."
                    enteredPin = ""
                }
            } else {
                viewModel.savePin(enteredPin)
                delay(300)
                navController.navigate(Screen.HomeDashboard.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }
    }

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                // Green lock illustration in circle
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(LightGreenSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isUnlockMode) "Enter PIN" else "Set PIN",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryText
                )
                Text(
                    text = if (isUnlockMode) "ऐप खोलने के लिए पिन दर्ज करें" else "अपने ऐप को सुरक्षित रखें",
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText
                )

                Spacer(modifier = Modifier.height(36.dp))

                // 4 PIN bullet circles
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    (0..3).forEach { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) ForestGreen else SurfaceWhite)
                                .border(1.5.dp, if (isFilled) ForestGreen else BorderColor, CircleShape)
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ErrorRed
                    )
                }
            }

            // Numeric Keypad (1 to 9, 0, delete)
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = digit.isNotEmpty()) {
                                        errorMessage = null
                                        if (digit == "DEL") {
                                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        } else if (enteredPin.length < 4) {
                                            enteredPin += digit
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (digit == "DEL") {
                                    Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = PrimaryText)
                                } else {
                                    Text(
                                        text = digit,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = PrimaryText
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