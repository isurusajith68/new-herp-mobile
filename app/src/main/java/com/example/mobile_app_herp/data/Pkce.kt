package com.example.mobile_app_herp.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PKCE (S256) matching the server's derivation exactly — base64url of
 * SHA-256(verifier) with no padding (apps/api/src/lib/pkce.ts). A mismatch in
 * padding or alphabet is the classic cause of a "PKCE failed" 400.
 *
 * 32 random bytes encode to 43 base64url chars, the minimum the /login and
 * /authorize schemas accept.
 */
object Pkce {
    fun newVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
