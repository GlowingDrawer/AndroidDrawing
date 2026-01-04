package com.example.ECGDemo.chart  // 如果你建在子包，比如 com.example.cgmdemo.chart 就改成对应包名

import android.graphics.Canvas
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * 用来规避 MPAndroidChart 在 drawValues 阶段可能出现的 NegativeArraySizeException。
 * 我们目前不需要在点上绘制具体数值，因此这里直接禁用 drawValues。
 */
class SafeLineChartRenderer(
    chart: LineDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler
) : LineChartRenderer(chart, animator, viewPortHandler) {

    override fun drawValues(c: Canvas) {
        // 不在每个点上绘制 value，避免 MPAndroidChart 在极端情况下计算 label 位置时崩溃
        // 如果以后真的需要显示数值，可以在这里自行实现（注意各种边界保护）
    }
}
