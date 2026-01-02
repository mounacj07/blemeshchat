package com.example.meshchat.data

import android.content.Context
import android.provider.Settings

class UserPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("mesh_chat_prefs", Context.MODE_PRIVATE)

    fun getUserId(): Short {
        val saved = prefs.getInt("user_id", -1)
        return if (saved != -1) {
            saved.toShort()
        } else {
            // FIX: Use ANDROID_ID to generate a consistent device-specific ID.
            // This ensures the user has a stable ID across app restarts.
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: java.util.UUID.randomUUID().toString()
            
            // We use the hash of the ANDROID_ID to create a short, yet unique, identifier.
            var hash = androidId.hashCode() and 0xFFFF
            
            // Ensure we don't accidentally generate 0 (Broadcast) or 0xFFFF (-1, Uninitialized).
            if (hash == 0 || hash == 0xFFFF) {
                hash = 1 // Fallback to 1 if we hit an edge case
            }
            
            val newId = hash.toShort()
            prefs.edit().putInt("user_id", newId.toInt()).apply()
            newId
        }
    }

    fun getUserName(): String {
        return prefs.getString("user_name", "")!!
    }

    fun saveUserName(name: String) {
        prefs.edit()
            .putString("user_name", name)
            .commit()
    }
}
