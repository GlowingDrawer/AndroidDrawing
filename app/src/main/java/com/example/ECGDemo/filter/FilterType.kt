package com.example.ECGDemo.filter

enum class FilterType {
    NONE,           // 不滤波
    MOVING_AVERAGE, // 滑动平均
    MEDIAN,         // 中值滤波
    KALMAN          // 一维卡尔曼
}
