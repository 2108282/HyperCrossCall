package com.xiaomi.hypercrosscall

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.xiaomi.hypercrosscall.hook.MirrorCallHook

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
        isEnableHookSharedPreferences = false
    }

    override fun onHook() = encase {
        loadApp("com.xiaomi.mirror") {
            loadHooker(MirrorCallHook)
        }
    }
}
