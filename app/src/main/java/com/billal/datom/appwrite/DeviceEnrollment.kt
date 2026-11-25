package com.billal.datom.appwrite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec

/**
 * Local enrollment preparation only. A generated key does not authorize cloud access.
 * A future backend must verify ownership and proof of possession before enrollment.
 */
object DeviceEnrollment {
    private const val ALIAS = "datom_enrollment_v1"
    private const val STORE = "AndroidKeyStore"

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(STORE).apply { load(null) }

    @Synchronized
    fun fingerprint(): String? {
        val key = keyStore().getCertificate(ALIAS)?.publicKey ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(key.encoded)
            .joinToString(":") { "%02x".format(it.toInt() and 0xff) }
    }

    @Synchronized
    fun prepare(): String {
        if (!keyStore().containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
            }.generateKeyPair()
        }
        return checkNotNull(fingerprint())
    }

    @Synchronized
    fun reset() {
        keyStore().deleteEntry(ALIAS)
    }

    fun removeLegacyApiKey(context: Context) {
        context.getSharedPreferences("appwrite_config", Context.MODE_PRIVATE)
            .edit().remove("api_key").apply()
    }
}
