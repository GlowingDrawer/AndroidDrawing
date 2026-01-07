package com.example.ECGDemo

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.Locale
import kotlin.math.roundToInt

class BloodPressureActivity : AppCompatActivity() {

    // ==========================================
    // ⬇️ 请在这里修改您的数值 (第一列 SBP, 第二列 DBP)
    // ==========================================
    private val staticData = listOf(
        113.8f to 80.1f,
        109.4f to 80.1f,
        112.7f to 84.1f,
        108.2f to 80.1f,
        113.7f to 75.1f,
    )
    // ==========================================

    private lateinit var chartBP: LineChart
    private lateinit var tvAvgSbp: TextView
    private lateinit var tvAvgDbp: TextView

    // 新增：状态标签控件
    private lateinit var tvSbpStatus: TextView
    private lateinit var tvDbpStatus: TextView

    private lateinit var tvCountInfo: TextView
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blood_pressure)

        initViews()
        initChart()

        // 核心逻辑：加载静态数据并计算
        processAndDisplayData()
    }

    private fun initViews() {
        chartBP = findViewById(R.id.chartBP)
        tvAvgSbp = findViewById(R.id.tvAvgSbp)
        tvAvgDbp = findViewById(R.id.tvAvgDbp)

        // 新增：绑定 XML 中的状态标签 ID
        tvSbpStatus = findViewById(R.id.tvSbpStatus)
        tvDbpStatus = findViewById(R.id.tvDbpStatus)

        tvCountInfo = findViewById(R.id.tvCountInfo)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
    }

    private fun initChart() {
        chartBP.description = Description().apply { text = "" } // 不显示描述
        chartBP.setTouchEnabled(true)
        chartBP.isDragEnabled = true
        chartBP.setScaleEnabled(true)
        chartBP.setPinchZoom(true)

        // X轴设置
        val xAxis = chartBP.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f // 间隔为1

        // Y轴设置
        chartBP.axisRight.isEnabled = false // 隐藏右侧Y轴
        chartBP.axisLeft.setDrawGridLines(true)
    }

    private fun processAndDisplayData() {
        if (staticData.isEmpty()) return

        val sbpEntries = ArrayList<Entry>()
        val dbpEntries = ArrayList<Entry>()

        var sbpSum = 0.0
        var dbpSum = 0.0

        // 遍历数据生成图表点和计算总和
        staticData.forEachIndexed { index, (sbp, dbp) ->
            val xIndex = index.toFloat()
            sbpEntries.add(Entry(xIndex, sbp))
            dbpEntries.add(Entry(xIndex, dbp))

            sbpSum += sbp
            dbpSum += dbp
        }

        // 1. 绘制图表
        val setSbp = LineDataSet(sbpEntries, "SBP (收缩压)").apply {
            color = Color.RED
            setCircleColor(Color.RED)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.LINEAR
        }

        val setDbp = LineDataSet(dbpEntries, "DBP (舒张压)").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.LINEAR
        }

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(setSbp)
        dataSets.add(setDbp)

        val lineData = LineData(dataSets)
        chartBP.data = lineData

        // 自动调整视图以显示所有数据
        chartBP.invalidate()
        chartBP.animateX(800) // 简单的动画效果

        // 2. 计算并显示平均值
        val count = staticData.size
        val avgSbp = sbpSum / count
        val avgDbp = dbpSum / count

        // 显示数值 (保留1位小数)
        tvAvgSbp.text = String.format(Locale.US, "%.1f", avgSbp)
        tvAvgDbp.text = String.format(Locale.US, "%.1f", avgDbp)
        tvCountInfo.text = "样本数: $count"

        // 3. 新增：更新状态标签 (传入四舍五入后的整数进行判断)
        updateStatus(tvSbpStatus, avgSbp.roundToInt(), isSbp = true)
        updateStatus(tvDbpStatus, avgDbp.roundToInt(), isSbp = false)
    }

    /**
     * 根据数值判断血压状态，并更新 TextView 的文字和背景颜色
     * @param view 需要更新的 TextView
     * @param value 血压平均值 (整数)
     * @param isSbp 是否为收缩压 (true: SBP, false: DBP)
     */
    private fun updateStatus(view: TextView, value: Int, isSbp: Boolean) {
        // 定义阈值
        val highLimit = if (isSbp) 120 else 80
        val lowLimit = if (isSbp) 90 else 60

        when {
            // 偏高
            value >= highLimit -> {
                view.text = "偏高"
                // 设置背景颜色为红色 (注意：需要 XML 中设置了 background drawable 才能用 setTint)
                view.background.setTint(Color.parseColor("#F44336"))
            }
            // 偏低
            value < lowLimit -> {
                view.text = "偏低"
                // 设置背景颜色为橙色
                view.background.setTint(Color.parseColor("#FF9800"))
            }
            // 正常
            else -> {
                view.text = "正常"
                // 设置背景颜色为绿色
                view.background.setTint(Color.parseColor("#4CAF50"))
            }
        }
    }
}