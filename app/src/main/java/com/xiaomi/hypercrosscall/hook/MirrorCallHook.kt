package com.xiaomi.hypercrosscall.hook

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.os.PowerManager
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
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

        // 利用 DexKit 动态定位混淆类名，不受 HyperOS 3.0 OTA 更新影响
        try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(apkPath)?.use { bridge ->
                // 1. 定位 MirrorCallService (包含调试标签 "MirrorCallService")
                val callServiceList = bridge.findClass {
                    searchPackages("com.xiaomi.mirror")
                    matcher {
                        usingStrings("MirrorCallService", "onOppositeTerminalConnected")
                    }
                }
                mirrorCallServiceClassName = callServiceList.firstOrNull()?.name

                // 2. 定位 CallRelayAudioManager (包含调试标签 "CallRelayAudioManager")
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

        // 默认回退类名 (根据逆向所得)
        val targetCallService = mirrorCallServiceClassName ?: "com.xiaomi.mirror.relay.G"
        val targetAudioMgr = callAudioMgrClassName ?: "i2.B"

        YLog.info(msg = "Using CallService: $targetCallService, AudioMgr: $targetAudioMgr", tag = TAG)

        // -------------------------------------------------------------
        // Hook 1: 解除 Phone to Phone 握手互斥拦截
        // -------------------------------------------------------------
        targetCallService.toClass(appClassLoader).apply {
            // Hook onOppositeTerminalConnected(g0)
            method {
                name = "F"
            }.hook {
                before {
                    val g0Param = args[0] ?: return@before
                    val g0Class = g0Param.javaClass
                    
                    // 获取对端设备平台类型
                    val isOppositePhone = g0Class.method { name = "o0" }.get(g0Param).boolean()
                    val isOppositePad = g0Class.method { name = "k0" }.get(g0Param).boolean()

                    YLog.info(msg = "onOppositeTerminalConnected called. oppositePhone=$isOppositePhone, oppositePad=$isOppositePad", tag = TAG)

                    // 若对端是手机，动态让系统认为对端兼具 PAD 属性，放行后续初始化
                    if (isOppositePhone) {
                        YLog.info(msg = "Enabling Phone-to-Phone cross call tunnel!", tag = TAG)
                        // 动态放行：伪装对端响应 k0() 为 true
                        g0Class.method { name = "k0" }.hook {
                            replaceToTrue()
                        }
                    }
                }
            }

            // Hook onCallStart: 接通电话时的音频设备接管
            method {
                name = "C"
            }.hook {
                after {
                    YLog.info(msg = "onCallStart detected! Routing audio to earpiece & acquiring proximity lock", tag = TAG)
                    appContext?.let { ctx ->
                        routeAudioToEarpiece(ctx)
                        acquireProximityLock(ctx)
                    }
                }
            }

            // Hook onCallEnd: 通话挂断释放
            method {
                name = "B"
            }.hook {
                after {
                    YLog.info(msg = "onCallEnd detected! Releasing audio and proximity lock", tag = TAG)
                    releaseProximityLock()
                }
            }
        }

        // -------------------------------------------------------------
        // Hook 2: 副机接听端强制启用 Pad AudioSink 参数 & 听筒输出
        // -------------------------------------------------------------
        targetAudioMgr.toClass(appClassLoader).apply {
            // Hook 构造器，确保副机在手机形态下也按从机模式配置
            constructor().hook {
                after {
                    // d 原本是 true ^ Build.IS_TABLET (手机上为 true)
                    // 改为 false，使手机走从机 AudioSink 协议
                    field { name = "d" }.get(instance).set(false)
                    YLog.info(msg = "CallRelayAudioManager initialized, overridden isTablet field to true", tag = TAG)
                }
            }
        }
    }

    /**
     * 将音频流定向到物理听筒 (TYPE_BUILTIN_EARPIECE)
     */
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

            // 检查并提示系统级硬件 AEC
            if (AcousticEchoCanceler.isAvailable()) {
                YLog.info(msg = "System hardware AcousticEchoCanceler (AEC) is available and active.", tag = TAG)
            }
        } catch (e: Throwable) {
            YLog.error(msg = "routeAudioToEarpiece failed: ${e.message}", tag = TAG)
        }
    }

    /**
     * 贴耳防误触息屏锁
     */
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
                proximityWakeLock?.acquire(2 * 60 * 60 * 1000L) // 最长2小时保护
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
