package com.xiaomi.hypercrosscall

import android.app.Application
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

class CrossCallApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
    }
}
