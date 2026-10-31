package com.xiaomi.hypercrosscall.hook

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object DeviceTypeHook : YukiBaseHooker() {

    private const val TAG = "HyperCrossCall-Device"
    private const val DEVICE_TYPE_PAD = 2 // TrustedDeviceInfo.DEVICE_TYPE_PAD

    override fun onHook() {
        YLog.info(msg = "DeviceTypeHook injected into: $packageName", tag = TAG)

        val appClassLoader = appClassLoader ?: return

        // 1. Hook TrustedDeviceInfo.getDeviceType: 强制对外将本机声明为平板 PAD(2)
        try {
            "com.xiaomi.continuity.networking.TrustedDeviceInfo".toClass(appClassLoader).apply {
                method { name = "getDeviceType" }.hook {
                    replaceTo(DEVICE_TYPE_PAD)
                }
            }
            YLog.info(msg = "Hooked TrustedDeviceInfo.getDeviceType -> PAD(2)", tag = TAG)
        } catch (e: Throwable) {
            YLog.warn(msg = "TrustedDeviceInfo not found in $packageName: ${e.message}", tag = TAG)
        }

        // 2. Hook RemoteDeviceInfo: 平台类型标记
        try {
            "com.xiaomi.mirror.RemoteDeviceInfo".toClass(appClassLoader).apply {
                method { name = "getPlatform" }.hook {
                    replaceTo("AndroidPad")
                }
            }
        } catch (_: Throwable) {}

        // 3. Hook DeviceManager.getRemoteDevice: 如果问本机类型，声明为 PAD
        try {
            "com.milink.runtime.lyra.device.DeviceManager".toClass(appClassLoader).apply {
                method { name = "getDeviceType" }.hook {
                    replaceTo("PAD")
                }
            }
            YLog.info(msg = "Hooked DeviceManager.getDeviceType -> PAD", tag = TAG)
        } catch (_: Throwable) {}
    }
}
