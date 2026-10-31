# HyperCrossCall (小米跨设备双手机通话流转 LSPosed 模块)

专为小米 **HyperOS 3.0 (Android 16 / API 36)** 打造的现代 LSPosed 模块，旨在打破小米互联“跨设备通话仅支持手机流转到平板/PC”的硬编码限制，使两台小米手机之间亦能实现完整的跨设备通话流转。

---

## 🌟 核心特性

1. **Phone-to-Phone 握手放行**：
   - 动态 Hook `com.xiaomi.mirror` 底层信令，绕过平台类型硬编码拦截，在双手机间顺利建立通话中继。
2. **物理听筒定向流转（TYPE_BUILTIN_EARPIECE）**：
   - 摆脱平板只能免提扬声器外放的缺陷，接听电话时自动调用 Android `AudioManager` 将下行 AAC 语音路由至机身顶部听筒。
3. **贴耳感应熄屏防误触**：
   - 激活 Android 系统底层 `PROXIMITY_SCREEN_OFF_WAKE_LOCK`，接听贴耳时屏幕触控自动灭屏，拿开自动点亮。
4. **硬件级回声消除（AEC）**：
   - 音频采集会话绑定 `VOICE_COMMUNICATION` 与硬件级 `AcousticEchoCanceler`，从芯片 HAL 层消除回声与啸叫。
5. **系统原生 Telecom / CallKit 融合**：
   - 注册 Android `ConnectionService`，当手机 B 正在进行流转通话且本地 SIM 卡来电时，系统原生弹出“保持并接听 / 结束当前”仲裁菜单，绝不发生音频流强行抢占崩溃。

---

## 🚀 编译与安装

项目已配置 GitHub Actions 自动云端编译：
1. 每次推送到 `main` 分支均会自动触发 GitHub Actions 构建。
2. 前往 **GitHub Actions** 标签页即可直接下载最新打好的 Debug APK 产物。
3. 在两台手机上安装 APK，在 LSPosed 作用域中勾选 **小米互联服务 / 小米妙享桌面 (`com.xiaomi.mirror`)**，重启对应作用域应用或重启手机即可生效。
