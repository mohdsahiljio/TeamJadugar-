package com.teamjadugar.apk

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

class VpnPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1001)
        } else {
            startVpn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                startVpn()
            } else {
                finish()
            }
        }
    }

    private fun startVpn() {
        startService(Intent(this, VpnService::class.java))
        finish()
    }
}
