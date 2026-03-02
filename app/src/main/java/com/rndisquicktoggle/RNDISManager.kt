package com.rndisquicktoggle

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class RNDISManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var isRNDISEnabled = false
    private var adbHost = "127.0.0.1"
    private var adbPort = 5555

    fun setADBConnection(host: String, port: Int) {
        adbHost = host
        adbPort = port
    }

    fun toggleRNDIS(callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                if (isRNDISEnabled) {
                    disableRNDIS()
                    isRNDISEnabled = false
                    handler.post { callback(false, context.getString(R.string.rndis_disabled)) }
                } else {
                    enableRNDIS()
                    isRNDISEnabled = true
                    handler.post { callback(true, context.getString(R.string.rndis_enabled)) }
                }
            } catch (e: Exception) {
                handler.post { callback(false, e.message ?: context.getString(R.string.error)) }
            }
        }.start()
    }

    private fun enableRNDIS() {
        executeADBCommand("svc usb setFunctions rndis")
        executeADBCommand("settings put global tether_dun_required 0")
    }

    private fun disableRNDIS() {
        executeADBCommand("svc usb setFunctions none")
    }

    fun checkRNDISStatus(): Boolean {
        return try {
            val result = executeADBCommand("cat /sys/class/android_usb/android0/functions")
            result.contains("rndis", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun executeADBCommand(command: String): String {
        val socket = Socket(adbHost, adbPort)
        try {
            val writer = OutputStreamWriter(socket.getOutputStream())
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.write("000Cshell:$command\n")
            writer.flush()

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            writer.close()
            reader.close()

            return output.toString()
        } finally {
            socket.close()
        }
    }

    fun isADBConnected(): Boolean {
        return try {
            val socket = Socket(adbHost, adbPort)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun testADBConnection(callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val result = executeADBCommand("echo connected")
                if (result.contains("connected", ignoreCase = true)) {
                    handler.post { callback(true, "ADB connection successful") }
                } else {
                    handler.post { callback(false, "ADB connection failed") }
                }
            } catch (e: Exception) {
                handler.post { callback(false, "ADB connection failed: ${e.message}") }
            }
        }.start()
    }
}
