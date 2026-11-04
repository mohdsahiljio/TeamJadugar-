package com.teamjadugar.apk

import android.content.Context
import android.content.SharedPreferences

object SessionStore {
    private const val PREF = "sable_session"
    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun currentKey(ctx: Context): String =
        sp(ctx).getString("key", "") ?: ""
    fun saveKey(ctx: Context, key: String) {
        sp(ctx).edit().putString("key", key).apply()
    }
    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
