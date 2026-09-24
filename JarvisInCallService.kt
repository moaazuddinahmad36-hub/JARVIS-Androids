package com.example.jarvis

import android.telecom.Call
import android.telecom.InCallService

class JarvisInCallService : InCallService() {
    companion object {
        var currentCall: Call? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
    }

    override fun onCallRemoved(call: Call) {
        if (currentCall == call) currentCall = null
        super.onCallRemoved(call)
    }
}
