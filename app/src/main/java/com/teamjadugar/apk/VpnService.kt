package com.teamjadugar.apk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream

class VpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            // 1. Notification channel banao
            createNotificationChannel()
            val notification = createNotification()
            
            // 2. Service ko Foreground me chalao (App kill nahi hoga)
            startForeground(1, notification)
            
            // 3. VPN start karo
            startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("0.0.0.0", 0)
            builder.setSession("Jadugar Sniffer")
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
                            output.write(packet)
                        }
                    }
                } catch (e: Exception) {}
            }.start()
        } catch (e: Exception) {}
    }

    private fun parseBgmiPacket(packet: ByteArray): String? {
        if (packet.size < 28) return null
        if (((packet[0].toInt() shr 4) and 0xF) != 4) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null 

        val headerLen = (packet[0].toInt() and 0xF) * 4
        val dstIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
        val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or (packet[headerLen + 3].toInt() and 0xFF)

        if (dstIp.startsWith("10.") || dstIp.startsWith("192.168.") || dstIp.startsWith("172.") || dstIp == "127.0.0.1") return null
        if (dstPort < 10000 || dstPort > 30000) return null
        if (dstPort == 17001 || dstPort == 20000 || dstPort == 20001 || dstPort == 20002) return null

        return "$dstIp:$dstPort"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "vpn_channel",
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("Team Jadugar")
            .setContentText("Sniffing BGMI IP...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {}
    }
    
    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
