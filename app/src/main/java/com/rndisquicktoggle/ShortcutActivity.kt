package com.rndisquicktoggle

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShortcutActivity : AppCompatActivity() {

    private lateinit var rndisManager: RNDISManager
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("RNDISPrefs", Context.MODE_PRIVATE)
        rndisManager = RNDISManager(this)
        
        val host = sharedPreferences.getString("adb_host", "127.0.0.1")
        val port = sharedPreferences.getInt("adb_port", 5555)
        rndisManager.setADBConnection(host!!, port)

        if (!rndisManager.isADBConnected()) {
            Toast.makeText(this, R.string.adb_not_connected, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        rndisManager.toggleRNDIS { success, message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
