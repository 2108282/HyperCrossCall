package com.xiaomi.hypercrosscall.hook

import android.service.notification.StatusBarNotification
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object MilinkCallHook : YukiBaseHooker() {

    private const val TAG = "HyperCrossCall-Milink"

    override fun onHook() {
        YLog.info(msg = "MilinkCallHook injected into com.milink.service", tag = TAG)

        val appClassLoader = appClassLoader ?: return

        // 1. Hook 接收端设备订阅白名单：无条件放行任意主机的通知流转请求
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
            YLog.info(msg = "SubscriptionManager.getSubDeviceApp hooked", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook SubscriptionManager: ${e.message}", tag = TAG)
        }

        // 2. 解除 DeviceSubInfo 开关限制，强制为已开启 (1)
        try {
            "com.xiaomi.dist.notification.common.data.DeviceSubInfo".toClass(appClassLoader).apply {
                method { name = "getDeviceSwitch" }.hook {
                    replaceTo(1)
                }
            }
        } catch (_: Throwable) {}

        // 3. 解除 VoipTransHelper 的白名单拦截
        try {
            "com.xiaomi.dist.notification.listener.voip.VoipTransHelper".toClass(appClassLoader).apply {
                method { name = "isLocalVoipSupported" }.hook { replaceToTrue() }
                method { name = "isRemoteVoipSupported" }.hook { replaceToTrue() }
                method { name = "isCallRelaySwitchOn" }.hook { replaceToTrue() }
                method { name = "isVoipSupported" }.hook { replaceToTrue() }
            }
            YLog.info(msg = "VoipTransHelper hooked to true", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook VoipTransHelper: ${e.message}", tag = TAG)
        }

        // 4. 解除 AppNotificationFilter.filterCall 的电话通知拦截
        try {
            "com.xiaomi.dist.notification.listener.handle.AppNotificationFilter".toClass(appClassLoader).apply {
                method { name = "filterCall" }.hook { replaceToFalse() }
            }
        } catch (_: Throwable) {}

        // 5. 关键：解除 CallHfpHandler 硬件属性拦截，让来电通知能够正常流转
        try {
            "com.xiaomi.dist.notification.handler.handler.CallHfpHandler".toClass(appClassLoader).apply {
                method { name = "isSupportCall" }.hook {
                    replaceToTrue()
                }
            }
            YLog.info(msg = "CallHfpHandler.isSupportCall hooked to true", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook CallHfpHandler: ${e.message}", tag = TAG)
        }

        // 6. 接收端收到来电通知时，直接调用 showNotification 弹出原生通知卡片
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
            YLog.info(msg = "NotificationPresenter.handlerNotification hooked", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook NotificationPresenter: ${e.message}", tag = TAG)
        }
    }
}
