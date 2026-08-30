package com.example.netconnect_tool.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.netconnect_tool.data.model.Carrier

class CredentialStore(context: Context) {

    /** 凭据是否存储在 EncryptedSharedPreferences 中（false = 明文回退，设置页应提示） */
    var isEncrypted: Boolean = true
        private set

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // EncryptedSharedPreferences 可能因设备无锁屏/旧设备失败，退化到普通 SharedPreferences。
        // 明文落盘必须留下痕迹：日志告警 + isEncrypted=false，供设置页提示用户。
        Log.w(TAG, "EncryptedSharedPreferences 创建失败，凭据将以明文回退存储", e)
        isEncrypted = false
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    fun saveCredentials(account: String, password: String, carrier: Carrier) {
        prefs.edit()
            .putString(KEY_ACCOUNT, account)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_CARRIER, carrier.suffix)
            .apply()
    }

    fun getCredentials(): SavedCredentials? {
        val account = prefs.getString(KEY_ACCOUNT, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        val carrierSuffix = prefs.getString(KEY_CARRIER, "") ?: ""
        return SavedCredentials(account, password, Carrier.fromSuffix(carrierSuffix))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "CredentialStore"
        private const val FILE_NAME = "netconnect_credentials"
        private const val FALLBACK_FILE = "netconnect_credentials_fallback"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val KEY_CARRIER = "carrier"
    }
}

data class SavedCredentials(
    val account: String,
    val password: String,
    val carrier: Carrier
)
