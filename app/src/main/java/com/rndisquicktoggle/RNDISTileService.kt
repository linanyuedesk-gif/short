package com.rndisquicktoggle

import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Handler
import android.os.Looper

class RNDISTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updateTileState() }
    private var rndisManager: RNDISManager? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("RNDISPrefs", Context.MODE_PRIVATE)
        rndisManager = RNDISManager(this)
        loadSettings()
    }

    private fun loadSettings() {
        val host = sharedPreferences.getString("adb_host", "127.0.0.1")
        val port = sharedPreferences.getInt("adb_port", 5555)
        rndisManager?.setADBConnection(host!!, port)
    }

    override fun onStartListening() {
        super.onStartListening()
        loadSettings()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        val tile = qsTile
        if (tile.state == Tile.STATE_UNAVAILABLE) {
            return
        }

        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        Thread {
            try {
                rndisManager?.toggleRNDIS { success, message ->
                    handler.post {
                        updateTileState()
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    tile.state = Tile.STATE_INACTIVE
                    tile.updateTile()
                }
            }
        }.start()
    }

    private fun updateTileState() {
        val tile = qsTile
        val manager = rndisManager

        if (manager == null || !manager.isADBConnected()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.adb_required)
            tile.updateTile()
            return
        }

        val isEnabled = manager.checkRNDISStatus()
        tile.state = if (isEnabled) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.toggle_rndis)
        tile.updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        handler.removeCallbacks(updateRunnable)
    }
}
