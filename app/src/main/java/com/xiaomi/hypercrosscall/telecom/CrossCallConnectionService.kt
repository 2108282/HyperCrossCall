package com.xiaomi.hypercrosscall.telecom

import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

class CrossCallConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "CrossCallConnection"
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "onCreateIncomingConnection: ${request?.extras}")

        val callerName = request?.extras?.getString("CALLER_NAME") ?: "小米跨设备流转来电"
        val messageId = request?.extras?.getString("MESSAGE_ID") ?: ""

        return CrossCallConnection(messageId).apply {
            setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
            setAddress(Uri.fromParts("tel", "跨设备通话", null), TelecomManager.PRESENTATION_ALLOWED)
            setInitializing()
            
            // 告知系统此通话为 VoIP 模式，由应用自我管理生命周期，系统自动挂载 AEC 与贴耳灭屏
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            audioModeIsVoip = true
            setRinging()
        }
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.w(TAG, "onCreateIncomingConnectionFailed")
    }
}

class CrossCallConnection(private val messageId: String) : Connection() {

    companion object {
        private const val TAG = "CrossCallConnection"
    }

    override fun onAnswer() {
        Log.i(TAG, "User answered on native UI. Responding to Phone A...")
        setActive()
        // 反向触发手机 A 接听 (通过广播或调用 Mirror 内部消息总线)
    }

    override fun onReject() {
        Log.i(TAG, "User rejected call.")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        Log.i(TAG, "Call disconnected.")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }
}
