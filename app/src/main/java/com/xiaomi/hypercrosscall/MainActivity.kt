package com.xiaomi.hypercrosscall

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.highcapable.yukihookapi.YukiHookAPI

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "hyper_cross_call_settings"
        const val KEY_WORK_MODE = "work_mode"
        const val MODE_BIDIRECTIONAL = 0 // 模式一：双端自适应对等互联 (推荐)
        const val MODE_SINGLE_SINK = 1   // 模式二：副机单端接听 (免改主力机)

        const val KEY_EARPIECE = "force_earpiece"
        const val KEY_PROXIMITY = "proximity_lock"
        const val KEY_AEC = "hardware_aec"
        const val KEY_TELECOM = "telecom_callkit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 状态检查
        val tvStatus = findViewById<TextView>(R.id.tv_module_status)
        val tvDeviceInfo = findViewById<TextView>(R.id.tv_device_info)

        val isActive = try {
            YukiHookAPI.Status.isModuleActive
        } catch (_: Throwable) {
            false
        }

        if (isActive) {
            tvStatus.text = "● LSPosed 模块已完全激活并注入生效"
            tvStatus.setTextColor(getColor(R.color.status_active))
        } else {
            // 如果仅在目标应用中生效，给出明确提示
            tvStatus.text = "● 模块已就绪 (LSPosed 作用域请勾选 HyperCrossCall 与 小米互联)"
            tvStatus.setTextColor(getColor(R.color.status_active))
        }

        tvDeviceInfo.text = "当前宿主: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"

        // 模式切换 (二选一)
        val rgWorkMode = findViewById<RadioGroup>(R.id.rg_work_mode)
        val rbBidi = findViewById<RadioButton>(R.id.rb_mode_bidirectional)
        val rbSink = findViewById<RadioButton>(R.id.rb_mode_single_sink)

        val currentMode = prefs.getInt(KEY_WORK_MODE, MODE_BIDIRECTIONAL)
        if (currentMode == MODE_SINGLE_SINK) {
            rbSink.isChecked = true
        } else {
            rbBidi.isChecked = true
        }

        rgWorkMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_mode_single_sink) {
                MODE_SINGLE_SINK
            } else {
                MODE_BIDIRECTIONAL
            }
            prefs.edit().putInt(KEY_WORK_MODE, mode).apply()
        }

        // 功能开关绑定
        bindSwitch(R.id.switch_earpiece, KEY_EARPIECE, true)
        bindSwitch(R.id.switch_proximity, KEY_PROXIMITY, true)
        bindSwitch(R.id.switch_aec, KEY_AEC, true)
        bindSwitch(R.id.switch_telecom, KEY_TELECOM, true)
    }

    private fun bindSwitch(switchId: Int, key: String, defaultValue: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val switchView = findViewById<Switch>(switchId)
        switchView.isChecked = prefs.getBoolean(key, defaultValue)
        switchView.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
        }
    }
}
