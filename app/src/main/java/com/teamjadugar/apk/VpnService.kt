package com.teamjadugar.apk

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

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
            
            // CANARY TRICK: Blocking true karo taaki packet turant mile
            builder.setBlocking(true)
            
            vpnInterface = builder.establish()
            isRunning = true

            Thread {
                try {
                    val input = FileInputStream(vpnInterface!!.fileDescriptor)
                    val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                    val buffer = ByteArray(65535)

                    while (isRunning) {
                        val length = input.read(buffer)
                        if (length > 0) {
                            val packet = buffer.copyOf(length)
                            
                            val ipPort = parseBgmiPacket(packet)
                            if (ipPort != null) {
                                val parts = ipPort.split(":")
                                FloatingService.onIpFound(parts[0], parts[1])
                                stopVpn()
                                break
                            }
                            
                            // Forward packet (Game hang nahi hoga)
                            output.write(packet)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VPN_ERROR", "Error reading: ${e.message}")
                }
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseBgmiPacket(packet: ByteArray): String? {
        if (packet.size < 28) return null
        
        // IPv4 Check
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return null
        
        // UDP Check
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null

        val headerLen = (packet[0].toInt() and 0xF) * 4
        if (packet.size < headerLen + 8) return null

        val dstIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or (packet[headerLen + 3].toInt() and 0xFF)

        // Local IP skip
        if (dstIp.startsWith("10.") || dstIp.startsWith("192.168.") || dstIp.startsWith("172.") || dstIp == "127.0.0.1") return null

        // BGMI Port Range Filter
        if (dstPort < 10000 || dstPort > 30000) return null
        if (dstPort == 17001 || dstPort == 20000 || dstPort == 20001 || dstPort == 20002) return null

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
