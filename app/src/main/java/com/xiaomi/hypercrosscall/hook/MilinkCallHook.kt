package com.xiaomi.hypercrosscall.hook

import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object MilinkCallHook : YukiBaseHooker() {

    private const val TAG = "HyperCrossCall-Milink"

    override fun onHook() {
        YLog.info(msg = "MilinkCallHook injected into com.milink.service", tag = TAG)

        val appClassLoader = appClassLoader ?: return

        // 1. 解除 VoipTransHelper 的白名单拦截 (强制让本地与远端支持通话流转)
        try {
            "com.xiaomi.dist.notification.listener.voip.VoipTransHelper".toClass(appClassLoader).apply {
                method { name = "isLocalVoipSupported" }.hook {
                    replaceToTrue()
                }
                method { name = "isRemoteVoipSupported" }.hook {
                    replaceToTrue()
                }
                method { name = "isCallRelaySwitchOn" }.hook {
                    replaceToTrue()
                }
                method { name = "isVoipSupported" }.hook {
                    replaceToTrue()
                }
            }
            YLog.info(msg = "VoipTransHelper methods hooked to true", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook VoipTransHelper: ${e.message}", tag = TAG)
        }

        // 2. 解除 AppNotificationFilter.filterCall 的电话通知拦截 (绝不丢弃 call 类别)
        try {
            "com.xiaomi.dist.notification.listener.handle.AppNotificationFilter".toClass(appClassLoader).apply {
                method { name = "filterCall" }.hook {
                    replaceToFalse() // 返回 false 表示不丢弃、正常转发！
                }
            }
            YLog.info(msg = "AppNotificationFilter.filterCall hooked to false", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook AppNotificationFilter: ${e.message}", tag = TAG)
        }

        // 3. 关键：解除 NotificationHandler 必须要求【主机息屏锁屏】才转发的限制！
        // 允许主机即使在亮屏操作时，也向副机正常推送来电通知与灵动岛胶囊
        try {
            "com.xiaomi.dist.notification.listener.handle.NotificationHandler".toClass(appClassLoader).apply {
                method { name = "isNotificationValid" }.hook {
                    before {
                        val sbn = args[0] as? StatusBarNotification ?: return@before
                        val category = sbn.notification?.category
                        val pkg = sbn.packageName

                        // 如果是来电通知 (com.android.incallui / category == "call")，直接强制放行
                        if ("com.android.incallui" == pkg || "call" == category) {
                            YLog.info(msg = "Incoming call notification intercepted, force allowing trans!", tag = TAG)
                            result = true
                        }
                    }
                }
            }
            YLog.info(msg = "NotificationHandler.isNotificationValid hooked for call category", tag = TAG)
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook NotificationHandler: ${e.message}", tag = TAG)
        }
    }
}
