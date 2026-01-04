package com.example.ECGDemo.filter

/**
 * 所有滤波器的统一接口
 */
interface SignalFilter {
    /** 输入原始值，返回滤波后的值 */
    fun apply(value: Double): Double

    /** 重置内部状态（切数据段/重启实验时可以调用） */
    fun reset()
}

/** 完全不做处理的滤波器 */
class NoFilter : SignalFilter {
    override fun apply(value: Double): Double = value
    override fun reset() {}
}


/**
 * 简单滑动平均滤波
 */
class MovingAverageFilter(
    private val windowSize: Int
) : SignalFilter {

    private val buffer = ArrayDeque<Double>()

    override fun apply(value: Double): Double {
        buffer.addLast(value)
        if (buffer.size > windowSize) {
            buffer.removeFirst()
        }
        if (buffer.isEmpty()) return 0.0
        return buffer.sum() / buffer.size
    }

    override fun reset() {
        buffer.clear()
    }
}

/**
 * 中值滤波
 */
class MedianFilter(
    private val windowSize: Int
) : SignalFilter {

    private val buffer = ArrayDeque<Double>()

    override fun apply(value: Double): Double {
        buffer.addLast(value)
        if (buffer.size > windowSize) {
            buffer.removeFirst()
        }
        if (buffer.isEmpty()) return 0.0
        val sorted = buffer.sorted()
        return sorted[sorted.size / 2]
    }

    override fun reset() {
        buffer.clear()
    }
}


/**
 * 一维卡尔曼滤波器，用于单通道信号
 */
class KalmanFilter1D(
    var q: Double = 0.01,  // 过程噪声
    var r: Double = 0.1    // 测量噪声
) : SignalFilter {

    private var x: Double? = null // 当前估计
    private var p: Double = 0.1   // 当前估计误差

    override fun apply(value: Double): Double {
        if (x == null) {
            x = value
            return value
        }
        // 预测
        val xPred = x!!
        val pPred = p + q

        // 更新
        val k = pPred / (pPred + r)  // 卡尔曼增益
        x = xPred + k * (value - xPred)
        p = (1 - k) * pPred
        return x!!
    }

    override fun reset() {
        x = null
        p = 0.1
    }
}