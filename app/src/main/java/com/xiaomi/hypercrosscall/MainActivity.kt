package com.xiaomi.hypercrosscall

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.highcapable.yukihookapi.hook.factory.isModuleActive

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "hyper_cross_call_settings"
        const val KEY_WORK_MODE = "work_mode"
        const val MODE_BIDIRECTIONAL = 0 // 双端自适应
        const val MODE_SINGLE_SINK = 1   // 副机单端伪装 (免改主机)
        const val MODE_SINGLE_SOURCE = 2 // 仅主机模式

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

        if (isModuleActive) {
            tvStatus.text = "● LSPosed 模块已激活并接管通信层"
            tvStatus.setTextColor(getColor(R.color.status_active))
        } else {
            tvStatus.text = "○ 模块未在 LSPosed 中激活 (请在框架中勾选本模块)"
            tvStatus.setTextColor(getColor(R.color.status_inactive))
        }

        tvDeviceInfo.text = "当前宿主: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"

        // 模式切换
        val rgWorkMode = findViewById<RadioGroup>(R.id.rg_work_mode)
        val rbBidi = findViewById<RadioButton>(R.id.rb_mode_bidirectional)
        val rbSink = findViewById<RadioButton>(R.id.rb_mode_single_sink)
        val rbSource = findViewById<RadioButton>(R.id.rb_mode_single_source)

        val currentMode = prefs.getInt(KEY_WORK_MODE, MODE_BIDIRECTIONAL)
        when (currentMode) {
            MODE_SINGLE_SINK -> rbSink.isChecked = true
            MODE_SINGLE_SOURCE -> rbSource.isChecked = true
            else -> rbBidi.isChecked = true
        }

        rgWorkMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_mode_single_sink -> MODE_SINGLE_SINK
                R.id.rb_mode_single_source -> MODE_SINGLE_SOURCE
                else -> MODE_BIDIRECTIONAL
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
