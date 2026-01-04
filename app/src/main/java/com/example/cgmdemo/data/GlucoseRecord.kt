package com.example.cgmdemo.data

/**
 * 只关注葡萄糖和电压的单点记录
 */
data class GlucoseRecord(
    val seconds: Double,        // 时间（秒）
    val glucoseCurrent: Double, // 葡萄糖电流（mA）
    val voltage: Double,        // 电压（V）
    val receiveTime: String     // 接收时间字符串，仅用于显示/导出
)
