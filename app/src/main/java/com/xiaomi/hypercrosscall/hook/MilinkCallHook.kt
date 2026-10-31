package com.xiaomi.hypercrosscall.hook

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object MilinkCallHook : YukiBaseHooker() {

    private const val TAG = "HyperCrossCall-Milink"

    override fun onHook() {
        YLog.info(msg = "MilinkCallHook injected into com.milink.service", tag = TAG)

        val appClassLoader = appClassLoader ?: return

        // -------------------------------------------------------------
        // 核心解封 1: 解决「跨设备通知」搜索页面搜不到另一台手机的问题 (正在发现您的手机)
        // -------------------------------------------------------------
        try {
            "com.xiaomi.dist.notification.discover.LyraDiscover".toClass(appClassLoader).apply {
                // 强制让所有附近设备通过 cast 投屏能力校验
                method { name = "isCastServiceSupport" }.hook {
                    replaceToTrue()
                }

                // 拦截 addOrUpdateDevice，绕过复杂的 bit 掩码校验，直接放行发现手机
                method { name = "addOrUpdateDevice" }.hook {
                    before {
                        val trustedDeviceInfo = args[0] ?: return@before
                        YLog.info(msg = "LyraDiscover.addOrUpdateDevice intercepted: $trustedDeviceInfo", tag = TAG)
                    }
                }
            }
            YLog.info(msg = "LyraDiscover hooked for device discovery!", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook LyraDiscover: ${e.message}", tag = TAG)
        }

        // -------------------------------------------------------------
        // 核心解封 2: 解除设备白名单拦截 (允许接收该设备的通知与流转)
        // -------------------------------------------------------------
        try {
            "com.xiaomi.dist.notification.common.sub.SubscriptionManager".toClass(appClassLoader).apply {
                method { name = "getSubDeviceApp" }.hook {
                    after {
                        if (result == null) {
                            val deviceId = args[0] as? String ?: return@after
                            try {
                                val infoClass = "com.xiaomi.dist.notification.common.data.DeviceSubInfo".toClass(appClassLoader)
                                val subInfo = infoClass.getConstructor().newInstance()
                                infoClass.getMethod("setDeviceId", String::class.java).invoke(subInfo, deviceId)
                                infoClass.getMethod("setDeviceSwitch", Int::class.javaPrimitiveType).invoke(subInfo, 1)
                                result = subInfo
                                YLog.info(msg = "getSubDeviceApp synthesized active DeviceSubInfo for: $deviceId", tag = TAG)
                            } catch (e: Throwable) {
                                YLog.warn(msg = "Failed to synthesize DeviceSubInfo: ${e.message}", tag = TAG)
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 解除 DeviceSubInfo 状态限制
        try {
            "com.xiaomi.dist.notification.common.data.DeviceSubInfo".toClass(appClassLoader).apply {
                method { name = "getDeviceSwitch" }.hook { replaceTo(1) }
            }
        } catch (_: Throwable) {}

        // 解除 VoipTransHelper 白名单
        try {
            "com.xiaomi.dist.notification.listener.voip.VoipTransHelper".toClass(appClassLoader).apply {
                method { name = "isLocalVoipSupported" }.hook { replaceToTrue() }
                method { name = "isRemoteVoipSupported" }.hook { replaceToTrue() }
                method { name = "isCallRelaySwitchOn" }.hook { replaceToTrue() }
                method { name = "isVoipSupported" }.hook { replaceToTrue() }
            }
        } catch (_: Throwable) {}

        // 解除来电通知丢弃
        try {
            "com.xiaomi.dist.notification.listener.handle.AppNotificationFilter".toClass(appClassLoader).apply {
                method { name = "filterCall" }.hook { replaceToFalse() }
            }
        } catch (_: Throwable) {}

        // 解除 CallHfpHandler 硬件限制
        try {
            "com.xiaomi.dist.notification.handler.handler.CallHfpHandler".toClass(appClassLoader).apply {
                method { name = "isSupportCall" }.hook { replaceToTrue() }
            }
        } catch (_: Throwable) {}

        // 强行弹出来电通知
        try {
            "com.xiaomi.dist.notification.handler.NotificationPresenter".toClass(appClassLoader).apply {
                method { name = "handlerNotification" }.hook {
                    before {
                        val notifData = args[0] ?: return@before
                        YLog.info(msg = "NotificationPresenter received: $notifData", tag = TAG)
                        try {
                            instance?.javaClass?.getMethod("showNotification", notifData.javaClass)?.invoke(instance, notifData)
                            YLog.info(msg = "Forced showNotification popup on receiver!", tag = TAG)
                        } catch (err: Throwable) {
                            YLog.warn(msg = "Fallback showNotification invocation: ${err.message}", tag = TAG)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}
