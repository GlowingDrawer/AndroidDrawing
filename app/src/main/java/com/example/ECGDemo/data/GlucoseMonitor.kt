package com.example.ECGDemo.data

import com.example.ECGDemo.filter.FilterManager
import com.example.ECGDemo.filter.FilterType
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.round

/**
 * 负责：
 *  - 从 JSON 提取 Seconds / Glucose / Volt
 *  - 转换成实际物理量（电流 mA / 电压 V）
 *  - 维护 时间-电流 / 电压-电流 的缓存
 *  - 维护记录列表用于导出 CSV
 *
 * 不负责：
 *  - 蓝牙 / 串口
 *  - 图表刷新（交给 Activity）
 */
class GlucoseMonitor(
    private val filterManager: FilterManager = FilterManager(
        type = FilterType.KALMAN,
        windowSize = 5,
        kalmanQ = 0.01,
        kalmanR = 0.1
    )
) {

    companion object {
        private const val ADC_VALUE_PER_VOLT = 1240.9091   // ADC 每伏的码值
        private const val REF_VOLT = 1.5                   // 参考电压
        private const val TIME_GAIN = 1000.0               // Seconds / 1000 -> 秒
        private const val GLUCOSE_GAIN = 200.0 / 1000.0    // mA

        private const val MAX_TIME_SPAN = 300.0            // 时间-电流保留最近 300 s
        private const val MAX_VOLT_POINTS = 600            // 电压-电流最多保留 600 点
    }

    // 是否启用滤波（默认 true）
    private var filterEnabled: Boolean = true

    // 时间-电流： (t, I[mA])
    val glucoseTimeData: MutableList<Pair<Double, Double>> = mutableListOf()

    // 电压-电流： (V, I[mA])
    val voltageGlucoseData: MutableList<Pair<Double, Double>> = mutableListOf()

    // 导出用记录
    val records: MutableList<GlucoseRecord> = mutableListOf()

    /**
     * 外部调用：切换是否启用滤波
     */
    fun setFilterEnabled(enabled: Boolean) {
        if (filterEnabled != enabled) {
            filterEnabled = enabled
            // 模式切换时重置滤波器内部状态，避免历史状态污染
            filterManager.reset()
        }
    }

    /**
     * 处理一帧 JSON 数据，只关注 Seconds / Glucose / Volt
     */
    fun processJson(obj: JSONObject): GlucoseRecord {
        val secondsRaw = obj.optDouble("Seconds", 0.0)
        val seconds = secondsRaw / TIME_GAIN

        val gluAdc = obj.optDouble("Glucose", 0.0)
        val voltAdc = obj.optDouble("Volt", 0.0)
        val receiveTime = obj.optString("receive_time", "")

        // ADC -> 电流（mA）
        val iRaw = adcToCurrent(gluAdc)
        // ADC -> 电压（V），保持与你 PC 端一致的算法
        val voltage = REF_VOLT - voltAdc / ADC_VALUE_PER_VOLT

        // 滤波 / 原始：由 filterEnabled 决定
        val iProcessed = if (filterEnabled) {
            filterManager.process(iRaw)
        } else {
            iRaw
        }

        // 更新时间-电流缓存
        glucoseTimeData.add(seconds to iProcessed)
        trimGlucoseTimeData()

        // 更新电压-电流缓存
        voltageGlucoseData.add(voltage to iProcessed)
        trimVoltageData()

        val record = GlucoseRecord(
            seconds = round4(seconds),
            glucoseCurrent = round4(iProcessed),
            voltage = round4(voltage),
            receiveTime = receiveTime
        )
        records.add(record)
        return record
    }

    /** 清空缓存（例如重新开始实验时） */
    fun reset() {
        glucoseTimeData.clear()
        voltageGlucoseData.clear()
        records.clear()
        filterManager.reset()
    }

    private fun adcToCurrent(adcValue: Double): Double {
        // 与 PC 端 adc_value_transform_to_current 同步
        val voltage = (adcValue - REF_VOLT * ADC_VALUE_PER_VOLT) / ADC_VALUE_PER_VOLT
        return voltage / GLUCOSE_GAIN     // 单位：mA
    }

    private fun trimGlucoseTimeData() {
        if (glucoseTimeData.isEmpty()) return
        val maxT = glucoseTimeData.last().first
        val minT = max(0.0, maxT - MAX_TIME_SPAN)
        val filtered = glucoseTimeData.filter { it.first >= minT }
        glucoseTimeData.clear()
        glucoseTimeData.addAll(filtered)
    }

    private fun trimVoltageData() {
        if (voltageGlucoseData.size > MAX_VOLT_POINTS) {
            val sub = voltageGlucoseData.takeLast(MAX_VOLT_POINTS)
            voltageGlucoseData.clear()
            voltageGlucoseData.addAll(sub)
        }
    }

    private fun round4(v: Double): Double {
        return round(v * 10000.0) / 10000.0
    }
}
