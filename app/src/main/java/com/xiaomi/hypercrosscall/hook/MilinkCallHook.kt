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

        // 1. 解除 VoipTransHelper 的白名单拦截
        try {
            "com.xiaomi.dist.notification.listener.voip.VoipTransHelper".toClass(appClassLoader).apply {
                method { name = "isLocalVoipSupported" }.hook { replaceToTrue() }
                method { name = "isRemoteVoipSupported" }.hook { replaceToTrue() }
                method { name = "isCallRelaySwitchOn" }.hook { replaceToTrue() }
                method { name = "isVoipSupported" }.hook { replaceToTrue() }
            }
            YLog.info(msg = "VoipTransHelper methods hooked to true", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook VoipTransHelper: ${e.message}", tag = TAG)
        }

        // 2. 解除 AppNotificationFilter.filterCall 的电话通知拦截
        try {
            "com.xiaomi.dist.notification.listener.handle.AppNotificationFilter".toClass(appClassLoader).apply {
                method { name = "filterCall" }.hook { replaceToFalse() }
            }
            YLog.info(msg = "AppNotificationFilter.filterCall hooked to false", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook AppNotificationFilter: ${e.message}", tag = TAG)
        }

        // 3. 关键：解除 NotificationPresenter 对来电的属性拦截，强制展示来电通知！
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

        // 4. 当副机 NotificationPresenter 收到来电时，即使 Hfp 不走，也强制调用 showNotification 弹出通知卡片
        try {
            "com.xiaomi.dist.notification.handler.NotificationPresenter".toClass(appClassLoader).apply {
                method { name = "handlerNotification" }.hook {
                    before {
                        val notifData = args[0] ?: return@before
                        YLog.info(msg = "NotificationPresenter.handlerNotification called: $notifData", tag = TAG)
                        // 直接调用 showNotification 弹窗，防止被任何前置 handler 吞掉
                        try {
                            instanceClass.getMethod("showNotification", notifData.javaClass).invoke(instance, notifData)
                            YLog.info(msg = "Force invoked showNotification for incoming call!", tag = TAG)
                        } catch (err: Throwable) {
                            YLog.warn(msg = "Fallback showNotification reflection: ${err.message}", tag = TAG)
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
