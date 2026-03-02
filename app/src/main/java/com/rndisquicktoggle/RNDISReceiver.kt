package com.rndisquicktoggle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class RNDISReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.rndisquicktoggle.TOGGLE_RNDIS") {
            val rndisManager = RNDISManager(context)
            
            if (!rndisManager.isRootAvailable()) {
                Toast.makeText(context, R.string.root_required, Toast.LENGTH_LONG).show()
                return
            }

            rndisManager.toggleRNDIS { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
