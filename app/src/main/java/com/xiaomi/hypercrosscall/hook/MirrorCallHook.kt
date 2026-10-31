package com.xiaomi.hypercrosscall.hook

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.os.PowerManager
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import org.luckypray.dexkit.DexKitBridge

object MirrorCallHook : YukiBaseHooker() {

    private const val TAG = "HyperCrossCall"
    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onHook() {
        YLog.info(msg = "MirrorCallHook injected into com.xiaomi.mirror", tag = TAG)

        val appClassLoader = appClassLoader ?: return
        val apkPath = appContext?.applicationInfo?.sourceDir ?: return

        var mirrorCallServiceClassName: String? = null
        var callAudioMgrClassName: String? = null

        // 利用 DexKit 动态定位混淆类名
        try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(apkPath)?.use { bridge ->
                val callServiceList = bridge.findClass {
                    searchPackages("com.xiaomi.mirror")
                    matcher {
                        usingStrings("MirrorCallService", "onOppositeTerminalConnected")
                    }
                }
                mirrorCallServiceClassName = callServiceList.firstOrNull()?.name

                val audioMgrList = bridge.findClass {
                    searchPackages("i2", "com.xiaomi.mirror")
                    matcher {
                        usingStrings("CallRelayAudioManager", "onSinkConnected")
                    }
                }
                callAudioMgrClassName = audioMgrList.firstOrNull()?.name
            }
        } catch (e: Throwable) {
            YLog.warn(msg = "DexKit search fallback to direct reflection: ${e.message}", tag = TAG)
        }

        val targetCallService = mirrorCallServiceClassName ?: "com.xiaomi.mirror.relay.G"
        val targetAudioMgr = callAudioMgrClassName ?: "i2.B"

        YLog.info(msg = "CallService: $targetCallService, AudioMgr: $targetAudioMgr", tag = TAG)

        // -------------------------------------------------------------
        // Hook 1: 精准局部伪装 —— 仅在妙享握手和终端描述上对外宣告为 AndroidPad
        // -------------------------------------------------------------
        try {
            // 握手包对外发送时将 os 设为 AndroidPad，主力机便会识别为平板并放行通话流转
            "com.xiaomi.mirror.message.HandshakeMessage".toClass(appClassLoader).apply {
                method { name = "fill" }.hook {
                    before {
                        try {
                            val osField = instanceClass.getField("os")
                            osField.set(instance, "AndroidPad")
                            YLog.info(msg = "HandshakeMessage.fill: successfully disguised os as AndroidPad", tag = TAG)
                        } catch (_: Throwable) {}
                    }
                }
            }

            // RemoteDeviceInfo 对外提供平台名称时返回 AndroidPad
            "com.xiaomi.mirror.RemoteDeviceInfo".toClass(appClassLoader).apply {
                method { name = "getPlatform" }.hook {
                    replaceTo("AndroidPad")
                }
            }
            YLog.info(msg = "Hooked HandshakeMessage & RemoteDeviceInfo to AndroidPad", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook handshake disguise: ${e.message}", tag = TAG)
        }

        // -------------------------------------------------------------
        // Hook 2: 本地通话中继接管与生命周期放行
        // -------------------------------------------------------------
        targetCallService.toClass(appClassLoader).apply {
            method {
                name = "F"
            }.hook {
                before {
                    val g0Param = args[0] ?: return@before
                    val g0Class = g0Param.javaClass
                    
                    val isOppositePhone = g0Class.method { name = "o0" }.get(g0Param).boolean()
                    YLog.info(msg = "onOppositeTerminalConnected called. oppositePhone=$isOppositePhone", tag = TAG)

                    // 本机始终允许与对方建立通话连接
                    g0Class.method { name = "k0" }.hook {
                        replaceToTrue()
                    }
                }
            }

            // 接听瞬间音频输出重定向到机身听筒 & 激活贴耳灭屏
            method {
                name = "C"
            }.hook {
                after {
                    YLog.info(msg = "onCallStart detected! Routing audio to earpiece", tag = TAG)
                    appContext?.let { ctx ->
                        routeAudioToEarpiece(ctx)
                        acquireProximityLock(ctx)
                    }
                }
            }

            // 通话挂断后释放
            method {
                name = "B"
            }.hook {
                after {
                    YLog.info(msg = "onCallEnd detected! Releasing earpiece and proximity lock", tag = TAG)
                    releaseProximityLock()
                }
            }
        }

        // -------------------------------------------------------------
        // Hook 3: 副机以从机 (AudioSink) 模式运行，准备接收通话音频
        // -------------------------------------------------------------
        try {
            val audioMgrClass = targetAudioMgr.toClass(appClassLoader)
            audioMgrClass.constructor().hook {
                after {
                    try {
                        val fieldD = audioMgrClass.getDeclaredField("d")
                        fieldD.isAccessible = true
                        fieldD.setBoolean(instance, false) // false 即以平板从机模式启动 AudioSink
                        YLog.info(msg = "CallRelayAudioManager field 'd' forced to false (AudioSink ready)", tag = TAG)
                    } catch (e: Throwable) {
                        YLog.error(msg = "Failed to override field 'd': ${e.message}", tag = TAG)
                    }
                }
            }
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook AudioMgr constructor: ${e.message}", tag = TAG)
        }
    }

    private fun routeAudioToEarpiece(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    val success = audioManager.setCommunicationDevice(earpiece)
                    YLog.info(msg = "Set communication device to earpiece: $success", tag = TAG)
                } else {
                    audioManager.isSpeakerphoneOn = false
                }
            } else {
                audioManager.isSpeakerphoneOn = false
            }

            if (AcousticEchoCanceler.isAvailable()) {
                YLog.info(msg = "Hardware AEC is active.", tag = TAG)
            }
        } catch (e: Throwable) {
            YLog.error(msg = "routeAudioToEarpiece failed: ${e.message}", tag = TAG)
        }
    }

    private fun acquireProximityLock(context: Context) {
        try {
            if (proximityWakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                proximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "HyperCrossCall:ProximityLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire(2 * 60 * 60 * 1000L)
                YLog.info(msg = "Proximity wake lock acquired.", tag = TAG)
            }
        } catch (e: Throwable) {
            YLog.error(msg = "acquireProximityLock failed: ${e.message}", tag = TAG)
        }
    }

    private fun releaseProximityLock() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
                YLog.info(msg = "Proximity wake lock released.", tag = TAG)
            }
        } catch (e: Throwable) {
            YLog.error(msg = "releaseProximityLock failed: ${e.message}", tag = TAG)
        }
    }
}
