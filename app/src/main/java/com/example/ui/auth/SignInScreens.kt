package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.StaffAccount
import com.example.data.model.StoreConfig
import com.example.ui.components.TurkishqLogoBadge
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SaffronGold
import kotlinx.coroutines.launch

@Composable
fun CloudSignInScreen(
  onAuthSuccess: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Card(
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
      ) {
        Column {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(190.dp)
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_hero_turkish_sweets),
              contentDescription = "TurkishQ Retail Showcase",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.25f), EmeraldDeep.copy(alpha = 0.92f))
                  )
                )
            )
            Row(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              TurkishqLogoBadge(size = 54.dp)
              Column {
                Text(
                  "TURKISHQ RETAIL",
                  style = MaterialTheme.typography.headlineMedium,
                  color = Color.White
                )
                Text(
                  "Commercial POS Billing & Management Suite",
                  style = MaterialTheme.typography.bodySmall,
                  color = SaffronGold
                )
              }
            }
          }

          Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Text(
              "Cloud Terminal Sign-In",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              "Sign in with your authorized Google account to connect securely to the TURKISHQ RETAIL Cloud Firestore database and unlock Master Admin or Staff POS sessions.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMsg != null) {
              Surface(
                color = CrimsonError.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = errorMsg!!,
                  color = CrimsonError,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(12.dp)
                )
              }
            }

            Button(
              onClick = {
                isLoading = true
                errorMsg = null
                AuthHelper.onGoogleSignInClicked(
                  context = context,
                  credentialManager = credentialManager,
                  onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                  },
                  onAuthError = { msg ->
                    isLoading = false
                    errorMsg = msg
                  },
                  scope = scope,
                  onAuthCancelled = {
                    isLoading = false
                  }
                )
              },
              enabled = !isLoading,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button")
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp
                )
              } else {
                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sign in with Google", style = MaterialTheme.typography.titleSmall)
              }
            }

            Text(
              "Currency: ₹ (INR) • Time Zone: Asia/Kolkata • End-to-End Firestore Security",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }
}

@Composable
fun InitialMasterAdminSetupScreen(
  userEmail: String,
  isBusy: Boolean,
  onInitialize: (
    businessName: String,
    address: String,
    contact: String,
    adminUsername: String,
    adminPassword: String,
    invoicePrefix: String,
    taxPercent: Double
  ) -> Unit,
  onSignOutCloud: () -> Unit
) {
  var businessName by remember { mutableStateOf("TURKISHQ RETAIL") }
  var address by remember { mutableStateOf("") }
  var contact by remember { mutableStateOf("") }
  var adminUsername by remember { mutableStateOf("masteradmin") }
  var adminPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var invoicePrefix by remember { mutableStateOf("TQR") }
  var taxInput by remember { mutableStateOf("5") }
  var validationError by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Card(
      modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          TurkishqLogoBadge(size = 48.dp)
          Column {
            Text("Initial Master Admin Setup", style = MaterialTheme.typography.headlineSmall)
            Text("Cloud Account: $userEmail", style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)
          }
        }
        Text(
          "Configure your TURKISHQ RETAIL store profile and Master Admin login credentials. Passwords are salted and hashed with PBKDF2-SHA256.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = businessName,
          onValueChange = { businessName = it },
          label = { Text("Business Name *") },
          modifier = Modifier.fillMaxWidth().testTag("setup_business_name")
        )
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Business Address") },
          modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = contact,
            onValueChange = { contact = it },
            label = { Text("Contact Number") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
          )
          OutlinedTextField(
            value = invoicePrefix,
            onValueChange = { invoicePrefix = it.uppercase().take(8) },
            label = { Text("Invoice Prefix *") },
            modifier = Modifier.weight(0.7f)
          )
        }
        OutlinedTextField(
          value = taxInput,
          onValueChange = { taxInput = it },
          label = { Text("Default Tax % (e.g. 5)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = adminUsername,
          onValueChange = { adminUsername = it },
          label = { Text("Master Admin Username *") },
          leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
          modifier = Modifier.fillMaxWidth().testTag("setup_admin_username")
        )
        OutlinedTextField(
          value = adminPassword,
          onValueChange = { adminPassword = it },
          label = { Text("Master Admin Password * (min 6 chars)") },
          leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("setup_admin_password")
        )
        OutlinedTextField(
          value = confirmPassword,
          onValueChange = { confirmPassword = it },
          label = { Text("Confirm Master Admin Password *") },
          leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("setup_admin_confirm_password")
        )

        if (validationError != null) {
          Text(validationError!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
        }

        Button(
          onClick = {
            when {
              businessName.isBlank() -> validationError = "Business name is required."
              adminUsername.trim().length < 3 -> validationError = "Admin username must be at least 3 characters."
              adminPassword.length < 6 -> validationError = "Admin password must be at least 6 characters."
              adminPassword != confirmPassword -> validationError = "Passwords do not match."
              invoicePrefix.trim().length < 2 -> validationError = "Invoice prefix must be 2–8 characters."
              else -> {
                validationError = null
                onInitialize(
                  businessName,
                  address,
                  contact,
                  adminUsername,
                  adminPassword,
                  invoicePrefix,
                  taxInput.toDoubleOrNull() ?: 5.0
                )
              }
            }
          },
          enabled = !isBusy,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("initialize_store_button")
        ) {
          if (isBusy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
          else Text("Initialize TURKISHQ RETAIL")
        }

        OutlinedButton(onClick = onSignOutCloud, modifier = Modifier.fillMaxWidth()) {
          Text("Switch Google Account")
        }
      }
    }
  }
}

@Composable
fun RoleTerminalLoginScreen(
  storeConfig: StoreConfig,
  linkedStaff: StaffAccount?,
  cloudEmail: String,
  onMasterAdminLogin: (username: String, password: String) -> String?,
  onStaffLogin: suspend (username: String, password: String) -> String?,
  onSignOutCloud: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val isLockedToStaff = linkedStaff != null
  var selectedTab by remember(isLockedToStaff) { mutableIntStateOf(if (isLockedToStaff) 1 else 0) }
  var username by remember(selectedTab, linkedStaff) {
    mutableStateOf(if (selectedTab == 1 && linkedStaff != null) linkedStaff.username else "")
  }
  var password by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var isSubmitting by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          TurkishqLogoBadge(size = 48.dp)
          Column(modifier = Modifier.weight(1f)) {
            Text(storeConfig.businessName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Cloud: $cloudEmail", style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary)
          }
        }

        if (!isLockedToStaff) {
          TabRow(selectedTabIndex = selectedTab) {
            Tab(
              selected = selectedTab == 0,
              onClick = {
                selectedTab = 0
                errorText = null
                password = ""
              },
              text = { Text("Master Admin") },
              icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
              modifier = Modifier.testTag("tab_master_admin_login")
            )
            Tab(
              selected = selectedTab == 1,
              onClick = {
                selectedTab = 1
                errorText = null
                password = ""
              },
              text = { Text("Staff Login") },
              icon = { Icon(Icons.Filled.Badge, contentDescription = null) },
              modifier = Modifier.testTag("tab_staff_login")
            )
          }
        } else {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(
                "Staff Terminal: ${linkedStaff!!.displayName} (@${linkedStaff.username})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
        }

        OutlinedTextField(
          value = username,
          onValueChange = { username = it; errorText = null },
          label = { Text(if (selectedTab == 0) "Master Admin Username" else "Staff Username") },
          leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
          singleLine = true,
          enabled = !(isLockedToStaff && selectedTab == 1),
          modifier = Modifier.fillMaxWidth().testTag("login_username_input")
        )

        OutlinedTextField(
          value = password,
          onValueChange = { password = it; errorText = null },
          label = { Text("Password") },
          leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(
                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = "Toggle password visibility"
              )
            }
          },
          singleLine = true,
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth().testTag("login_password_input")
        )

        if (errorText != null) {
          Surface(color = CrimsonError.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(errorText!!, color = CrimsonError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
          }
        }

        Button(
          onClick = {
            if (username.isBlank() || password.isBlank()) {
              errorText = "Enter both username and password."
              return@Button
            }
            if (selectedTab == 0 && !isLockedToStaff) {
              errorText = onMasterAdminLogin(username, password)
            } else {
              scope.launch {
                isSubmitting = true
                errorText = onStaffLogin(username, password)
                isSubmitting = false
              }
            }
          },
          enabled = !isSubmitting,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("operator_login_button")
        ) {
          if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
          } else {
            Icon(Icons.Filled.Storefront, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (selectedTab == 0) "Login as Master Admin" else "Login as Staff")
          }
        }

        OutlinedButton(
          onClick = onSignOutCloud,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
          modifier = Modifier.fillMaxWidth().testTag("sign_out_google_button")
        ) {
          Text("Sign Out of Google Account")
        }
      }
    }
  }
}
