package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.OperatorSession
import com.example.data.repository.TurkishqRepository
import com.example.ui.PosShell
import com.example.ui.auth.AuthHelper
import com.example.ui.auth.CloudSignInScreen
import com.example.ui.auth.InitialMasterAdminSetupScreen
import com.example.ui.auth.RoleTerminalLoginScreen
import com.example.ui.components.TurkishqLogoBadge
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.TurkishqRetailTheme
import com.example.viewmodel.StoreBootstrapState
import com.example.viewmodel.TurkishqViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      TurkishqRetailTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          TurkishqRetailApp()
        }
      }
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun TurkishqRetailApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val authFlow = remember { Firebase.auth.authStateFlow() }
  val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
  var checkingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null) {
      AuthHelper.attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { checkingAutoSignIn = false },
        onUnauthenticated = { checkingAutoSignIn = false },
        scope = scope
      )
    }
  }

  when {
    checkingAutoSignIn -> {
      LoadingSplashScreen("Connecting to TURKISHQ RETAIL Cloud...")
    }
    currentUser == null -> {
      CloudSignInScreen(onAuthSuccess = { checkingAutoSignIn = false })
    }
    else -> {
      val uid = currentUser!!.uid
      val email = currentUser!!.email.orEmpty()
      val repository = remember(context) { TurkishqRepository(context.applicationContext) }

      key(uid) {
        val viewModel: TurkishqViewModel = viewModel(
          key = uid,
          factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
              return TurkishqViewModel(repository, uid, email) as T
            }
          }
        )

        val bootstrapState by viewModel.bootstrapState.collectAsStateWithLifecycle()
        val session by viewModel.session.collectAsStateWithLifecycle()
        val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

        val performCloudSignOut = {
          AuthHelper.signOut(credentialManager, scope)
        }

        when (val bs = bootstrapState) {
          is StoreBootstrapState.Loading -> {
            LoadingSplashScreen("Loading Store Configuration...")
          }
          is StoreBootstrapState.NeedsInitialSetup -> {
            InitialMasterAdminSetupScreen(
              userEmail = email,
              isBusy = isBusy,
              onInitialize = { biz, addr, contact, adminUser, adminPass, prefix, tax ->
                viewModel.initializeMasterAdminStore(biz, addr, contact, adminUser, adminPass, prefix, tax)
              },
              onSignOutCloud = performCloudSignOut
            )
          }
          is StoreBootstrapState.Error -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                TurkishqLogoBadge(size = 64.dp)
                Text("Access Notice", style = MaterialTheme.typography.headlineSmall)
                Text(
                  text = bs.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = CrimsonError,
                  textAlign = TextAlign.Center
                )
                Button(onClick = { viewModel.loadBootstrap() }) {
                  Text("Retry Connection")
                }
                OutlinedButton(onClick = performCloudSignOut) {
                  Text("Switch Google Account")
                }
              }
            }
          }
          is StoreBootstrapState.Ready -> {
            if (session is OperatorSession.LoggedOut) {
              RoleTerminalLoginScreen(
                storeConfig = bs.storeConfig,
                linkedStaff = bs.linkedStaff,
                cloudEmail = email,
                onMasterAdminLogin = { u, p -> viewModel.loginAsMasterAdmin(u, p) },
                onStaffLogin = { u, p -> viewModel.loginAsStaff(u, p) },
                onSignOutCloud = performCloudSignOut
              )
            } else {
              PosShell(
                viewModel = viewModel,
                storeConfig = bs.storeConfig,
                session = session,
                onSignOutCloud = performCloudSignOut
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LoadingSplashScreen(message: String) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      TurkishqLogoBadge(size = 76.dp)
      Spacer(Modifier.height(18.dp))
      CircularProgressIndicator(modifier = Modifier.size(32.dp))
      Spacer(Modifier.height(14.dp))
      Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
