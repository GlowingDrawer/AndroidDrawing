package com.example.cgmdemo.filter

/**
 * 对外只暴露一个 FilterManager，内部根据配置选择不同的滤波器
 */
class FilterManager(
    type: FilterType,
    private var windowSize: Int = 5,
    private var kalmanQ: Double = 0.01,
    private var kalmanR: Double = 0.1
) {

    private var filter: SignalFilter = createFilter(type)

    var filterType: FilterType = type
        private set

    fun updateType(type: FilterType) {
        filterType = type
        filter = createFilter(type)
    }

    fun updateWindowSize(win: Int) {
        windowSize = win
        if (filterType == FilterType.MOVING_AVERAGE || filterType == FilterType.MEDIAN) {
            filter = createFilter(filterType)
        }
    }

    fun updateKalmanParams(q: Double, r: Double) {
        kalmanQ = q
        kalmanR = r
        if (filterType == FilterType.KALMAN) {
            filter = createFilter(filterType)
        }
    }

    /** 对单个数值进行滤波 */
    fun process(value: Double): Double = filter.apply(value)

    fun reset() = filter.reset()

    private fun createFilter(type: FilterType): SignalFilter {
        return when (type) {
            FilterType.NONE -> NoFilter()
            FilterType.MOVING_AVERAGE -> MovingAverageFilter(windowSize)
            FilterType.MEDIAN -> MedianFilter(windowSize)
            FilterType.KALMAN -> KalmanFilter1D(kalmanQ, kalmanR)
        }
    }
}
