package com.xiaomi.hypercrosscall.hook

import android.content.Context
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
        // 核心解封 1: 动态全账号设备发现 —— 彻底告别单设备硬编码！
        // 无论是 1 台还是 10,000 台设备，只要登录同账号，自动从底层网络总线拉取展示
        // -------------------------------------------------------------
        try {
            "com.xiaomi.dist.notification.common.sub.SubscriptionManager".toClass(appClassLoader).apply {
                // 动态提供账号下所有信任设备列表
                method { name = "getSubDeviceAppList" }.hook {
                    after {
                        val currentList = (result as? MutableList<Any>) ?: mutableListOf()
                        try {
                            val context = instance?.javaClass?.getDeclaredField("mContext")?.apply { isAccessible = true }?.get(instance) as? Context
                            if (context != null) {
                                val netMgrClass = "com.xiaomi.continuity.networking.NetworkingManager".toClass(appClassLoader)
                                val netMgr = netMgrClass.getMethod("getInstance", Context::class.java).invoke(null, context)
                                val trustedList = netMgrClass.getMethod("getTrustedDeviceList").invoke(netMgr) as? List<*>
                                val localDevice = netMgrClass.getMethod("getLocalDeviceInfo").invoke(netMgr)
                                val localId = localDevice?.javaClass?.getMethod("getDeviceId")?.invoke(localDevice) as? String

                                if (trustedList != null) {
                                    val infoClass = "com.xiaomi.dist.notification.common.data.DeviceSubInfo".toClass(appClassLoader)
                                    val subAppInfoClass = "com.xiaomi.dist.notification.common.data.DeviceSubInfo\$SubscriptionAppInfo".toClass(appClassLoader)
                                    val appConstructor = subAppInfoClass.getConstructor(String::class.java)

                                    val defaultApps = listOf(
                                        appConstructor.newInstance("com.android.phone"),
                                        appConstructor.newInstance("com.android.incallui"),
                                        appConstructor.newInstance("com.tencent.mm")
                                    )

                                    val existingIds = currentList.mapNotNull { item ->
                                        try { item.javaClass.getMethod("getDeviceId").invoke(item) as? String } catch (_: Throwable) { null }
                                    }.toSet()

                                    for (device in trustedList) {
                                        val did = device?.javaClass?.getMethod("getDeviceId")?.invoke(device) as? String ?: continue
                                        if (did == localId || existingIds.contains(did)) continue

                                        val dName = (try { device.javaClass.getMethod("getDeviceName").invoke(device) } catch (_: Throwable) { null }) as? String ?: "小米手机"
                                        val dType = (try { device.javaClass.getMethod("getDeviceType").invoke(device) as? Int } catch (_: Throwable) { null }) ?: 1

                                        val subInfo = infoClass.getConstructor(
                                            String::class.java, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, List::class.java
                                        ).newInstance(did, dName, dType, 1, defaultApps)

                                        currentList.add(subInfo)
                                        YLog.info(msg = "Dynamically injected trusted device into list: $dName ($did)", tag = TAG)
                                    }
                                    result = currentList
                                }
                            }
                        } catch (e: Throwable) {
                            YLog.error(msg = "Failed to dynamically populate trusted device list: ${e.message}", tag = TAG)
                        }
                    }
                }

                // 动态提供单设备的订阅凭证
                method { name = "getSubDeviceApp" }.hook {
                    after {
                        if (result == null) {
                            val deviceId = args[0] as? String ?: return@after
                            try {
                                val context = instance?.javaClass?.getDeclaredField("mContext")?.apply { isAccessible = true }?.get(instance) as? Context
                                val netMgrClass = "com.xiaomi.continuity.networking.NetworkingManager".toClass(appClassLoader)
                                val netMgr = netMgrClass.getMethod("getInstance", Context::class.java).invoke(null, context ?: appContext)
                                val trustedInfo = netMgrClass.getMethod("getTrustedDeviceInfo", String::class.java).invoke(netMgr, deviceId)

                                val dName = (try { trustedInfo?.javaClass?.getMethod("getDeviceName")?.invoke(trustedInfo) } catch (_: Throwable) { null }) as? String ?: "小米手机"
                                val dType = (try { trustedInfo?.javaClass?.getMethod("getDeviceType")?.invoke(trustedInfo) as? Int } catch (_: Throwable) { null }) ?: 1

                                val infoClass = "com.xiaomi.dist.notification.common.data.DeviceSubInfo".toClass(appClassLoader)
                                val subAppInfoClass = "com.xiaomi.dist.notification.common.data.DeviceSubInfo\$SubscriptionAppInfo".toClass(appClassLoader)
                                val appConstructor = subAppInfoClass.getConstructor(String::class.java)

                                val defaultApps = listOf(
                                    appConstructor.newInstance("com.android.phone"),
                                    appConstructor.newInstance("com.android.incallui"),
                                    appConstructor.newInstance("com.tencent.mm")
                                )

                                result = infoClass.getConstructor(
                                    String::class.java, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, List::class.java
                                ).newInstance(deviceId, dName, dType, 1, defaultApps)
                                YLog.info(msg = "Dynamically synthesized DeviceSubInfo for device: $deviceId", tag = TAG)
                            } catch (e: Throwable) {
                                YLog.warn(msg = "Failed to synthesize DeviceSubInfo: ${e.message}", tag = TAG)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook SubscriptionManager: ${e.message}", tag = TAG)
        }

        // -------------------------------------------------------------
        // 核心解封 2: 界面发现无条件添加 —— 废除 5 秒动画超时丢弃机制
        // -------------------------------------------------------------
        try {
            "com.miui.circulate.world.ui.connectivitysettings.Notification.DeviceReceiveStrategy\$c".toClass(appClassLoader).apply {
                method { name = "a" }.hook {
                    replaceUnit {
                        val notifiDevice = args[0] ?: return@replaceUnit
                        try {
                            val outerThis = instance?.javaClass?.getDeclaredField("this$0")?.apply { isAccessible = true }?.get(instance) ?: return@replaceUnit
                            val bMethod = outerThis.javaClass.getDeclaredMethod("B", notifiDevice.javaClass).apply { isAccessible = true }
                            val cVar = bMethod.invoke(outerThis, notifiDevice) ?: return@replaceUnit
                            val sMethod = outerThis.javaClass.getDeclaredMethod("s", cVar.javaClass, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                            sMethod.invoke(outerThis, cVar, false)
                            val cMethod = outerThis.javaClass.getDeclaredMethod("C").apply { isAccessible = true }
                            cMethod.invoke(outerThis)
                            YLog.info(msg = "DeviceReceiveStrategy force-added device to UI: $notifiDevice", tag = TAG)
                        } catch (e: Throwable) {
                            YLog.error(msg = "Error in DeviceReceiveStrategy.a replacement: ${e.message}", tag = TAG)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            YLog.error(msg = "Failed to hook DeviceReceiveStrategy: ${e.message}", tag = TAG)
        }

        // -------------------------------------------------------------
        // 核心解封 3: 扫描信道放行 (LyraDiscover)
        // -------------------------------------------------------------
        try {
            "com.xiaomi.dist.notification.discover.LyraDiscover".toClass(appClassLoader).apply {
                method { name = "isLocalServiceDataSupport" }.hook { replaceToTrue() }
                method { name = "isCastServiceSupport" }.hook { replaceToTrue() }
            }
        } catch (_: Throwable) {}

        // -------------------------------------------------------------
        // 核心解封 4: 来电通知与硬件拦截放行
        // -------------------------------------------------------------
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
