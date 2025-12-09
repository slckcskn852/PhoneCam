package com.phonecam

import android.app.Application
import org.webrtc.PeerConnectionFactory

class PhoneCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val options = PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }
}
