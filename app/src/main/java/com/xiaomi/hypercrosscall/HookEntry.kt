package com.xiaomi.hypercrosscall

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.xiaomi.hypercrosscall.hook.DeviceTypeHook
import com.xiaomi.hypercrosscall.hook.MilinkCallHook
import com.xiaomi.hypercrosscall.hook.MirrorCallHook

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
        isEnableHookSharedPreferences = false
    }

    override fun onHook() = encase {
        // 挂载自身以响应激活探测
        loadApp("com.xiaomi.hypercrosscall") {}

        // 挂载小米互联底层广播中心 (对外发布本机为平板设备，让主力机能够发现并推送)
        loadApp("com.xiaomi.mi_connect_service") {
            loadHooker(DeviceTypeHook)
        }

        // 挂载小米互联通信服务 (负责通知流转与来电捕获)
        loadApp("com.milink.service") {
            loadHooker(DeviceTypeHook)
            loadHooker(MilinkCallHook)
        }

        // 挂载小米妙享互联服务 (负责音频双向流转与电话中继状态机)
        loadApp("com.xiaomi.mirror") {
            loadHooker(DeviceTypeHook)
            loadHooker(MirrorCallHook)
        }
    }
}
