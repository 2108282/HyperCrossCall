package com.xiaomi.hypercrosscall.hook

import android.os.Handler
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object MilinkCallHook : YukiBaseHooker() {

    private const val TAG = "HyperCrossCall-Milink"

    override fun onHook() {
        YLog.info(msg = "MilinkCallHook injected into com.milink.service", tag = TAG)

        val appClassLoader = appClassLoader ?: return

        // -------------------------------------------------------------
        // 核心解封 1: 彻底打通「跨设备通知」设置页面的手机扫描与发现！
        // -------------------------------------------------------------
        try {
            "com.xiaomi.dist.notification.discover.LyraDiscover".toClass(appClassLoader).apply {
                // 1. 破解 startDiscovery 入口阻断：允许手机端正常注册底层扫描监听器
                method { name = "isLocalServiceDataSupport" }.hook {
                    replaceToTrue()
                }

                // 2. 破解投屏能力校验：无需支持 cast 即可被发现
                method { name = "isCastServiceSupport" }.hook {
                    replaceToTrue()
                }

                // 3. 拦截 addOrUpdateDevice：扫到任何信任设备，无视掩码直接加入发现列表并通知界面！
                method { name = "addOrUpdateDevice" }.hook {
                    before {
                        val trustedDeviceInfo = args[0] ?: return@before
                        try {
                            val getDeviceIdMethod = trustedDeviceInfo.javaClass.getMethod("getDeviceId")
                            val deviceId = getDeviceIdMethod.invoke(trustedDeviceInfo) as? String ?: return@before

                            val getDeviceNameMethod = try {
                                trustedDeviceInfo.javaClass.getMethod("getDeviceName")
                            } catch (_: Throwable) { null }
                            val deviceName = getDeviceNameMethod?.invoke(trustedDeviceInfo) as? String ?: "小米手机"

                            val getDeviceTypeMethod = trustedDeviceInfo.javaClass.getMethod("getDeviceType")
                            val deviceType = getDeviceTypeMethod.invoke(trustedDeviceInfo) as? Int ?: 1

                            // 反射构造 NotifiDevice
                            val notifiDeviceClass = "com.xiaomi.dist.notification.discover.NotifiDevice".toClass(appClassLoader)
                            val notifiDevice = notifiDeviceClass.getConstructor(String::class.java, Boolean::class.javaPrimitiveType)
                                .newInstance(deviceId, true)

                            notifiDeviceClass.getMethod("setMarketName", String::class.java).invoke(notifiDevice, deviceName)
                            notifiDeviceClass.getMethod("setDeviceType", Int::class.javaPrimitiveType).invoke(notifiDevice, deviceType)

                            // 获取 LyraDiscover 实例的内部字段
                            val listenerField = instanceClass?.getDeclaredField("mLyraDeviceListener")?.apply { isAccessible = true }
                            val lyraListener = listenerField?.get(instance)

                            val handlerField = instanceClass?.getDeclaredField("mHandler")?.apply { isAccessible = true }
                            val handler = handlerField?.get(instance) as? Handler

                            val devicesMapField = instanceClass?.getDeclaredField("mNotifiDeivces")?.apply { isAccessible = true }
                            val devicesMap = devicesMapField?.get(instance) as? MutableMap<String, Any>
                            devicesMap?.put(deviceId, notifiDevice)

                            // 关键：通知界面添加此设备！
                            if (lyraListener != null && handler != null) {
                                handler.post {
                                    try {
                                        lyraListener.javaClass.getMethod("onDeviceFound", notifiDeviceClass)
                                            .invoke(lyraListener, notifiDevice)
                                        YLog.info(msg = "Directly notified UI onDeviceFound: $deviceName ($deviceId)", tag = TAG)
                                    } catch (e: Throwable) {
                                        YLog.error(msg = "Failed to dispatch onDeviceFound to UI: ${e.message}", tag = TAG)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            YLog.error(msg = "Error in addOrUpdateDevice hook: ${e.message}", tag = TAG)
                        }
                    }
                }
            }
            YLog.info(msg = "LyraDiscover scan & UI notification pipeline completely unblocked!", tag = TAG)
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
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            "com.xiaomi.dist.notification.common.data.DeviceSubInfo".toClass(appClassLoader).apply {
                method { name = "getDeviceSwitch" }.hook { replaceTo(1) }
            }
        } catch (_: Throwable) {}

        try {
            "com.xiaomi.dist.notification.listener.voip.VoipTransHelper".toClass(appClassLoader).apply {
                method { name = "isLocalVoipSupported" }.hook { replaceToTrue() }
                method { name = "isRemoteVoipSupported" }.hook { replaceToTrue() }
                method { name = "isCallRelaySwitchOn" }.hook { replaceToTrue() }
                method { name = "isVoipSupported" }.hook { replaceToTrue() }
            }
        } catch (_: Throwable) {}

        try {
            "com.xiaomi.dist.notification.listener.handle.AppNotificationFilter".toClass(appClassLoader).apply {
                method { name = "filterCall" }.hook { replaceToFalse() }
            }
        } catch (_: Throwable) {}

        try {
            "com.xiaomi.dist.notification.handler.handler.CallHfpHandler".toClass(appClassLoader).apply {
                method { name = "isSupportCall" }.hook { replaceToTrue() }
            }
        } catch (_: Throwable) {}

        try {
            "com.xiaomi.dist.notification.handler.NotificationPresenter".toClass(appClassLoader).apply {
                method { name = "handlerNotification" }.hook {
                    before {
                        val notifData = args[0] ?: return@before
                        try {
                            instance?.javaClass?.getMethod("showNotification", notifData.javaClass)?.invoke(instance, notifData)
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}
