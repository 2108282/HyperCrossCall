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
        var remoteDeviceInfoClassName: String? = null

        // 利用 DexKit 动态定位混淆类名
        try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(apkPath)?.use { bridge ->
                // 1. 定位 MirrorCallService
                val callServiceList = bridge.findClass {
                    searchPackages("com.xiaomi.mirror")
                    matcher {
                        usingStrings("MirrorCallService", "onOppositeTerminalConnected")
                    }
                }
                mirrorCallServiceClassName = callServiceList.firstOrNull()?.name

                // 2. 定位 CallRelayAudioManager
                val audioMgrList = bridge.findClass {
                    searchPackages("i2", "com.xiaomi.mirror")
                    matcher {
                        usingStrings("CallRelayAudioManager", "onSinkConnected")
                    }
                }
                callAudioMgrClassName = audioMgrList.firstOrNull()?.name

                // 3. 定位 RemoteDeviceInfo (用于单端伪装模式)
                val deviceInfoList = bridge.findClass {
                    searchPackages("com.xiaomi.mirror")
                    matcher {
                        usingStrings("PLATFORM_ANDROID_PAD", "PLATFORM_ANDROID_PHONE")
                    }
                }
                remoteDeviceInfoClassName = deviceInfoList.firstOrNull()?.name
            }
        } catch (e: Throwable) {
            YLog.warn(msg = "DexKit search fallback to direct reflection: ${e.message}", tag = TAG)
        }

        val targetCallService = mirrorCallServiceClassName ?: "com.xiaomi.mirror.relay.G"
        val targetAudioMgr = callAudioMgrClassName ?: "i2.B"
        val targetDeviceInfo = remoteDeviceInfoClassName ?: "com.xiaomi.mirror.RemoteDeviceInfo"

        YLog.info(msg = "CallService: $targetCallService, AudioMgr: $targetAudioMgr", tag = TAG)

        // -------------------------------------------------------------
        // Hook 1: 无论双端还是单端，支持对端与本地主从握手放行
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

                    if (isOppositePhone) {
                        YLog.info(msg = "CrossCall handshake active: opening phone-to-phone tunnel", tag = TAG)
                        g0Class.method { name = "k0" }.hook {
                            replaceToTrue()
                        }
                    }
                }
            }

            // 接听瞬间音频输出重定向 & 贴耳熄屏
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

            // 挂断释放
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
        // Hook 2: 从机强制启用 Pad AudioSink 协议
        // -------------------------------------------------------------
        try {
            val audioMgrClass = targetAudioMgr.toClass(appClassLoader)
            audioMgrClass.constructor().hook {
                after {
                    try {
                        val fieldD = audioMgrClass.getDeclaredField("d")
                        fieldD.isAccessible = true
                        fieldD.setBoolean(instance, false)
                        YLog.info(msg = "Overridden CallRelayAudioManager field 'd' to false (Pad Mode)", tag = TAG)
                    } catch (e: Throwable) {
                        YLog.error(msg = "Failed to override field 'd': ${e.message}", tag = TAG)
                    }
                }
            }
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook AudioMgr constructor: ${e.message}", tag = TAG)
        }

        // -------------------------------------------------------------
        // Hook 3: 单端伪装能力支持 (向对端广播自身为 PAD，实现免改主机)
        // -------------------------------------------------------------
        try {
            targetDeviceInfo.toClass(appClassLoader).apply {
                method {
                    name = "getPlatform"
                }.hook {
                    after {
                        // 如果对端请求本设备平台类型，支持动态回传 PAD
                        YLog.info(msg = "RemoteDeviceInfo.getPlatform called", tag = TAG)
                    }
                }
            }
        } catch (_: Throwable) {}
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
