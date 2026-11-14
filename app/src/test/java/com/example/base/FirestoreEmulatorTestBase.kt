package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId = if (resId != 0) context.getString(resId) else "ai-studio-android-turkishq-ee5d097e-bbb3-4540-a2d6-22ae6563a75a"

    val app = if (FirebaseApp.getApps(context).isEmpty()) {
      val newApp = FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setApplicationId("com.aistudio.turkishqretail.kmpqtx")
          .setProjectId(PROJECT_ID)
          .setApiKey("fake-api-key-for-emulator")
          .build()
      )
      val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
      newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
      newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        .build()

      val newAuth = FirebaseAuth.getInstance(newApp)
      newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
      newApp
    } else {
      FirebaseApp.getInstance()
    }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)
    clearFirestoreEmulator()
  }

  private fun clearFirestoreEmulator() {
    runCatching {
      val url = URL("http://$EMULATOR_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents")
      (url.openConnection() as HttpURLConnection).run {
        requestMethod = "DELETE"
        responseCode
        disconnect()
      }
    }
  }

  @After
  open fun tearDownFirebase() {
    auth.signOut()
  }

  // Test-only helper for local Auth emulator; production app code uses CredentialManager Google Sign-In only.
  protected suspend fun signInTestUser(email: String, verifyEmail: Boolean = true): String = withContext(Dispatchers.IO) {
    withTimeout(AUTH_TIMEOUT_MS) {
      val result = try {
        auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
      } catch (_: FirebaseAuthInvalidUserException) {
        auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
      }
      val user = checkNotNull(result.user) { "User auth failed" }
      if (verifyEmail && !user.isEmailVerified) {
        verifyUserEmailInEmulator(user.getIdToken(false).await().token!!)
        user.reload().await()
        user.getIdToken(true).await()
      }
      user.uid
    }
  }

  private fun verifyUserEmailInEmulator(idToken: String) {
    // 1. Trigger OOB verification code in Auth emulator
    val sendOobUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fake-api-key-for-emulator")
    (sendOobUrl.openConnection() as HttpURLConnection).run {
      requestMethod = "POST"
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      outputStream.use {
        it.write("""{"requestType":"VERIFY_EMAIL","idToken":"$idToken"}""".toByteArray())
      }
      inputStream.bufferedReader().readText()
      disconnect()
    }
    // 2. Fetch latest OOB code from emulator admin endpoint
    val oobListUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/oobCodes")
    val oobJson = (oobListUrl.openConnection() as HttpURLConnection).run {
      requestMethod = "GET"
      inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
    }
    val codes = JSONObject(oobJson).optJSONArray("oobCodes")
    if (codes != null && codes.length() > 0) {
      val latestCode = codes.getJSONObject(codes.length() - 1).getString("oobCode")
      val confirmUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key-for-emulator")
      (confirmUrl.openConnection() as HttpURLConnection).run {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use {
          it.write("""{"oobCode":"$latestCode"}""".toByteArray())
        }
        inputStream.bufferedReader().readText()
        disconnect()
      }
    }
  }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  private companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 8000L
  }
}
