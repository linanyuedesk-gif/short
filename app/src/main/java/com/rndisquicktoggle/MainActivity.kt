package com.rndisquicktoggle

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var rndisManager: RNDISManager
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var createShortcutButton: Button
    private lateinit var addTileButton: Button
    private lateinit var testConnectionButton: Button
    private lateinit var adbHostText: EditText
    private lateinit var adbPortText: EditText
    private lateinit var saveSettingsButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("RNDISPrefs", Context.MODE_PRIVATE)
        rndisManager = RNDISManager(this)

        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        createShortcutButton = findViewById(R.id.createShortcutButton)
        addTileButton = findViewById(R.id.addTileButton)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        adbHostText = findViewById(R.id.adbHostText)
        adbPortText = findViewById(R.id.adbPortText)
        saveSettingsButton = findViewById(R.id.saveSettingsButton)

        loadSettings()
        updateStatus()

        toggleButton.setOnClickListener {
            if (!rndisManager.isADBConnected()) {
                Toast.makeText(this, R.string.adb_not_connected, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            toggleButton.isEnabled = false
            rndisManager.toggleRNDIS { success, message ->
                toggleButton.isEnabled = true
                updateStatus()
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        createShortcutButton.setOnClickListener {
            createShortcut()
        }

        addTileButton.setOnClickListener {
            openQuickSettingsToAddTile()
        }

        testConnectionButton.setOnClickListener {
            testConnection()
        }

        saveSettingsButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val host = sharedPreferences.getString("adb_host", "127.0.0.1")
        val port = sharedPreferences.getInt("adb_port", 5555)
        adbHostText.setText(host)
        adbPortText.setText(port.toString())
        rndisManager.setADBConnection(host!!, port)
    }

    private fun saveSettings() {
        val host = adbHostText.text.toString()
        val port = adbPortText.text.toString().toIntOrNull() ?: 5555

        sharedPreferences.edit()
            .putString("adb_host", host)
            .putInt("adb_port", port)
            .apply()

        rndisManager.setADBConnection(host, port)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        testConnectionButton.isEnabled = false
        rndisManager.testADBConnection { success, message ->
            testConnectionButton.isEnabled = true
            if (success) {
                Toast.makeText(this, R.string.connection_successful, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openQuickSettingsToAddTile() {
        try {
            val intent = Intent("android.settings.ACTION_QS_TILE_PREFERENCES")
            intent.putExtra("android.intent.extra.COMPONENT_NAME", "com.rndisquicktoggle/.RNDISTileService")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Please manually add the tile from Quick Settings",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent("android.settings.ACTION_QS_TILE_DETAILS")
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Swipe down from top of screen, tap edit, and add RNDIS Toggle",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatus() {
        val isEnabled = rndisManager.checkRNDISStatus()
        statusText.text = if (isEnabled) {
            getString(R.string.rndis_enabled)
        } else {
            getString(R.string.rndis_disabled)
        }
    }

    private fun createShortcut() {
        val shortcutIntent = Intent(this, ShortcutActivity::class.java)
        shortcutIntent.action = Intent.ACTION_CREATE_SHORTCUT

        val resultIntent = Intent()
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_name))
        
        val icon = android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher)
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon)

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
