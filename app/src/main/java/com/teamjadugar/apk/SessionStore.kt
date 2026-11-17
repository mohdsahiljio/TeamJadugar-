package com.teamjadugar.apk

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SessionStore {
    private const val PREF = "sable_secure_session"
    private const val AES_KEY = "SableSecure2026!" // 16 char key
    private const val AES_IV = "SecureInitVector"  // 16 char IV

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun encrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(AES_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(AES_IV.toByteArray())
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            Base64.encodeToString(cipher.doFinal(data.toByteArray()), Base64.DEFAULT)
        } catch (e: Exception) { data }
    }

    private fun decrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(AES_KEY.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(AES_IV.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            String(cipher.doFinal(Base64.decode(data, Base64.DEFAULT)))
        } catch (e: Exception) { data }
    }

    fun currentKey(ctx: Context): String {
        val encKey = sp(ctx).getString("key", "") ?: ""
        return if (encKey.isNotEmpty()) decrypt(encKey) else ""
    }
        
    fun saveKey(ctx: Context, key: String) {
        sp(ctx).edit().putString("key", encrypt(key)).apply()
    }
    
    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
