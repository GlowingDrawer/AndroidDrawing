package com.example.ECGDemo

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.Locale

class VitalSignsActivity : AppCompatActivity() {

    // ==========================================
    // ⬇️ 静态数据 (来自您提供的图片)
    // ==========================================
    private val staticHeartRateData = listOf(
        42.85714f, 68.33713f, 61.60164f, 53.76344f,
        54.34783f, 56.71078f, 52.91005f, 60.24096f,
        61.85567f, 57.91506f, 61.0998f, 62.63048f
    )

    private val staticRespirationData = listOf(
        18.63354f, 12.13347f, 19.96672f, 19.96672f,
        18.09955f, 17.96407f, 13.59003f
    )
    // ==========================================

    private lateinit var chartHeartRate: LineChart
    private lateinit var chartRespiration: LineChart
    private lateinit var tvAvgHeartRate: TextView
    private lateinit var tvAvgRespiration: TextView
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vital_signs)

        initViews()

        // 分别处理两组数据
        setupChart(chartHeartRate, staticHeartRateData, "心率 (BPM)", Color.parseColor("#E91E63"), tvAvgHeartRate)
        setupChart(chartRespiration, staticRespirationData, "呼吸 (RPM)", Color.parseColor("#2196F3"), tvAvgRespiration)
    }

    private fun initViews() {
        chartHeartRate = findViewById(R.id.chartHeartRate)
        chartRespiration = findViewById(R.id.chartRespiration)
        tvAvgHeartRate = findViewById(R.id.tvAvgHeartRate)
        tvAvgRespiration = findViewById(R.id.tvAvgRespiration)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
    }

    // 通用的图表设置与绘制函数
    private fun setupChart(
        chart: LineChart,
        dataList: List<Float>,
        label: String,
        lineColor: Int,
        tvAvg: TextView
    ) {
        // 1. 基础样式配置
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        chart.axisRight.isEnabled = false
        chart.axisLeft.setDrawGridLines(true)

        // 2. 转换数据
        val entries = ArrayList<Entry>()
        var sum = 0.0

        dataList.forEachIndexed { index, value ->
            entries.add(Entry(index.toFloat(), value))
            sum += value
        }

        // 3. 创建数据集
        val dataSet = LineDataSet(entries, label).apply {
            color = lineColor
            setCircleColor(lineColor)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER // 使用平滑曲线
            setDrawFilled(true) // 填充颜色
            fillColor = lineColor
            fillAlpha = 50
        }

        // 4. 显示图表
        chart.data = LineData(dataSet)
        chart.animateX(1000) // 动画
        chart.invalidate()

        // 5. 计算并显示平均值
        val avg = if (dataList.isNotEmpty()) sum / dataList.size else 0.0
        tvAvg.text = String.format(Locale.US, "Avg: %.2f", avg)
    }
}