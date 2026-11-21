package dev.yxuan.commit.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/** A Google ID token plus the raw nonce Supabase needs to verify it. */
data class GoogleToken(val idToken: String, val rawNonce: String)

/** Google sign-in through Credential Manager. */
object GoogleSignIn {
    /**
     * Shows the Google account picker and returns an ID token for [webClientId] (the OAuth *web*
     * client configured in Supabase). Google gets the SHA-256 of the nonce; Supabase the raw one.
     */
    suspend fun requestIdToken(activityContext: Context, webClientId: String): GoogleToken {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        return GoogleToken(credential.idToken, rawNonce)
    }
}
