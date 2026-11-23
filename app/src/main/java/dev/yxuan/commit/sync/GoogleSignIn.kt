package dev.yxuan.commit.sync

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A Google ID token plus the raw nonce Supabase needs to verify it. */
data class GoogleToken(val idToken: String, val rawNonce: String)

/** The user backed out of Google's account chooser. */
class SignInCancelledException : Exception("Sign-in cancelled.")

/**
 * Google sign-in through Play services' own account chooser.
 *
 * Not Credential Manager: on Android 14+ that always routes through the system picker
 * (com.android.credentialmanager), and on some Samsung builds that picker crashes during init,
 * leaving a dead, invisible activity over the app with the request never completing.
 */
object GoogleSignIn {
    /**
     * Shows Google's account chooser and returns an ID token for [webClientId] (the OAuth *web*
     * client configured in Supabase). Google gets the SHA-256 of the nonce; Supabase the raw one.
     */
    suspend fun requestIdToken(activity: ComponentActivity, webClientId: String): GoogleToken {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val client = Identity.getSignInClient(activity)
        val request = GetSignInIntentRequest.builder()
            .setServerClientId(webClientId)
            .setNonce(hashedNonce)
            .build()
        val pendingIntent = client.getSignInIntent(request).await()

        val data = suspendCancellableCoroutine { cont ->
            // Registered on the registry directly (not registerForActivityResult), so it can be
            // done after onCreate; unregistered as soon as the result arrives or the call is cancelled.
            val launcher = activity.activityResultRegistry.register(
                "google-sign-in-${UUID.randomUUID()}",
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) cont.resume(result.data!!)
                else cont.resumeWithException(SignInCancelledException())
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }

        val credential = client.getSignInCredentialFromIntent(data)
        val idToken = credential.googleIdToken ?: error("Google didn't return an ID token.")
        return GoogleToken(idToken, rawNonce)
    }
}
