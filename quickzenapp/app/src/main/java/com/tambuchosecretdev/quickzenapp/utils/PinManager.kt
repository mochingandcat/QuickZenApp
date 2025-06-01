package com.tambuchosecretdev.quickzenapp.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.io.File
import android.util.Log
import javax.crypto.AEADBadTagException
import java.security.GeneralSecurityException
import java.io.IOException

class PinManager(context: Context) {

    private val masterKeyAlias = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedPrefs(context, masterKeyAlias)
        } catch (e: AEADBadTagException) {
            handleEncryptionError(context, masterKeyAlias, e, "AEADBadTagException")
        } catch (e: GeneralSecurityException) {
            handleEncryptionError(context, masterKeyAlias, e, "GeneralSecurityException")
        } catch (e: IOException) {
            handleEncryptionError(context, masterKeyAlias, e, "IOException")
        }
    }

    private fun createEncryptedPrefs(context: Context, key: MasterKey): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun handleEncryptionError(context: Context, key: MasterKey, exception: Exception, type: String): SharedPreferences {
        Log.e("PinManager", "Error initializing EncryptedSharedPreferences ($type): ${exception.message}. Attempting to delete and recreate.")
        val prefsFile = File(context.applicationInfo.dataDir + "/shared_prefs/", PREFS_NAME + ".xml")
        if (prefsFile.exists()) {
            if (prefsFile.delete()) {
                Log.i("PinManager", "Successfully deleted corrupted SharedPreferences file: ${prefsFile.path}")
            } else {
                Log.e("PinManager", "Failed to delete corrupted SharedPreferences file: ${prefsFile.path}")
            }
        } else {
            Log.w("PinManager", "SharedPreferences file not found, proceeding to recreate: ${prefsFile.path}")
        }
        // Retry creating the SharedPreferences
        return createEncryptedPrefs(context, key)
    }

    companion object {
        private const val PREFS_NAME = "MynoteszenPinPrefs"
        private const val KEY_PIN_HASH = "pinHash"
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashedBytes.joinToString("") { "%02x".format(it) } // Convertir bytes a cadena hexadecimal
    }

    fun savePin(pin: String): Boolean {
        if (pin.length < 4) return false // Requerir un PIN de al menos 4 dígitos
        val pinHash = hashPin(pin)
        sharedPreferences.edit().putString(KEY_PIN_HASH, pinHash).apply()
        return true
    }

    fun checkPin(pin: String): Boolean {
        val storedHash = sharedPreferences.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == storedHash
    }

    fun isPinSet(): Boolean {
        return sharedPreferences.contains(KEY_PIN_HASH)
    }

    fun removePin(): Boolean {
        if (!isPinSet()) return false
        sharedPreferences.edit().remove(KEY_PIN_HASH).apply()
        return true
    }

    fun clearPin() {
        sharedPreferences.edit().remove(KEY_PIN_HASH).apply()
    }
} 