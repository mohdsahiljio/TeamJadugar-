package com.teamjadugar.apk

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class VpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!isRunning) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("0.0.0.0", 0)
            builder.setSession("Jadugar Sniffer")
            
            vpnInterface = builder.establish()
            isRunning = true

            Thread {
                try {
                    val input = FileInputStream(vpnInterface!!.fileDescriptor)
                    val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                    val buffer = ByteArray(32767)

                    while (isRunning) {
                        val length = input.read(buffer)
                        if (length > 0) {
                            val packet = buffer.copyOf(length)
                            
                            // Check for IP
                            val ipPort = parseBgmiPacket(packet)
                            if (ipPort != null) {
                                val parts = ipPort.split(":")
                                FloatingService.onIpFound(parts[0], parts[1])
                                stopVpn() 
                                break
                            }
                            // Forward traffic so game doesn't hang
                            output.write(packet)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseBgmiPacket(packet: ByteArray): String? {
        if (packet.size < 28) return null
        
        // IPv4 Check
        if (((packet[0].toInt() shr 4) and 0xF) != 4) return null
        
        // UDP Check
        if ((packet[9].toInt() and 0xFF) != 17) return null

        val headerLen = (packet[0].toInt() and 0xF) * 4
        if (packet.size < headerLen + 8) return null

        // Get Destination Port
        val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or (packet[headerLen + 3].toInt() and 0xFF)
        
        // Get Destination IP
        val dstIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        
        // Ignore local IPs
        if (dstIp.startsWith("10.") || dstIp.startsWith("192.168.") || dstIp.startsWith("172.") || dstIp == "127.0.0.1") return null
        if (dstIp == "0.0.0.0") return null

        // Return any UDP packet that is NOT local
        return "$dstIp:$dstPort"
    }

    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopSelf()
        } catch (e: Exception) {}
    }
    
    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
