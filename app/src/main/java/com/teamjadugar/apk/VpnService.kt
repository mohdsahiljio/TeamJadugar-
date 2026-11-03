package com.teamjadugar.apk

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class VpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    
    private val IGNORED_PORTS = setOf(20000, 20001, 20002, 17001)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("0.0.0.0", 0) 
            builder.setSession("Jadugar Sniffer")
            
            vpnInterface = builder.establish()
            isRunning = true

            Thread {
                val input = FileInputStream(vpnInterface!!.fileDescriptor)
                val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteArray(32767)

                while (isRunning) {
                    val length = input.read(buffer)
                    if (length > 0) {
                        val ipPacket = buffer.copyOf(length)
                        val ipPort = parseBgmiPacket(ipPacket)
                        if (ipPort != null) {
                            // Yahan hum IP aur PORT alag karke bhej rahe hain
                            val parts = ipPort.split(":")
                            val ip = parts[0]
                            val port = parts[1]
                            FloatingService.onIpFound(ip, port)
                            stopVpn() 
                            break
                        } else {
                            output.write(ipPacket)
                        }
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseBgmiPacket(packet: ByteArray): String? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return null
        
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null

        val headerLen = (packet[0].toInt() and 0xF) * 4
        if (packet.size < headerLen + 8) return null

        val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or (packet[headerLen + 3].toInt() and 0xFF)
        
        if (dstPort <= 10000 || dstPort >= 30000) return null
        if (dstPort in IGNORED_PORTS) return null

        val dstIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        
        if (dstIp.startsWith("10.") || dstIp.startsWith("192.168.") || dstIp.startsWith("172.")) return null

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
