package com.example.ECGDemo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent // [新增] 用于跳转 Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.widget.ArrayAdapter // [新增]
import android.widget.Spinner // [新增]
import com.example.ECGDemo.bt.BluetoothSerialManager
import com.example.ECGDemo.chart.SafeLineChartRenderer
import com.example.ECGDemo.data.GlucoseMonitor
import com.example.ECGDemo.data.JsonFrameDecoder
import com.example.ECGDemo.filter.FilterManager
import com.example.ECGDemo.filter.FilterType
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    // ---------- 常量 ----------
    private val REQ_BT_PERMISSIONS = 1001

    // ---------- 核心逻辑对象 ----------
    private lateinit var monitor: GlucoseMonitor
    private lateinit var btManager: BluetoothSerialManager
    private val jsonDecoder = JsonFrameDecoder()

    // ---------- UI ----------
    private lateinit var tvStatus: TextView
    private lateinit var spDevices: Spinner

    private lateinit var spMode: Spinner
    private lateinit var btnRefreshDevices: Button
    private lateinit var btnConnect: Button

    // [新增] 跳转到心电模式的按钮
    private lateinit var btnGoToHeart: Button

    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnResume: Button

    private lateinit var cbShowTime: CheckBox
    private lateinit var cbHex: CheckBox
    private lateinit var tvReceive: TextView
    private lateinit var btnClearReceive: Button
    private lateinit var btnSaveCsv: Button
    private lateinit var scrollReceive: ScrollView

    // Tab + 面板
    private lateinit var btnTabReceive: Button
    private lateinit var btnTabChart: Button
    private lateinit var receivePanel: View
    private lateinit var chartPanel: View

    // 图 + 表
    private lateinit var chartVoltGlucose: LineChart
    private lateinit var tableData: TableLayout

    // 当前选择的蓝牙设备
    private var currentDevice: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        initModeSpinner()
        initChart()
        initEvents()

        // 监测模块（滤波仍然用卡尔曼）
        val filterManager = FilterManager(
            type = FilterType.KALMAN,
            windowSize = 5,
            kalmanQ = 0.01,
            kalmanR = 0.1
        )
        monitor = GlucoseMonitor(filterManager)

        // 蓝牙管理器：只管连接/收发
        btManager = BluetoothSerialManager(
            BluetoothAdapter.getDefaultAdapter(),
            lifecycleScope
        ).apply {
            listener = object : BluetoothSerialManager.Listener {
                override fun onConnected(device: BluetoothDevice) {
                    currentDevice = device

                    // Android 12+：读取 device.name 也需要 BLUETOOTH_CONNECT 权限
                    val deviceName: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val hasConnectPermission =
                            ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED

                        if (hasConnectPermission) {
                            device.name ?: "未知设备"
                        } else {
                            "未知设备"
                        }
                    } else {
                        device.name ?: "未知设备"
                    }

                    tvStatus.text = "状态: 已连接 $deviceName"
                    tvStatus.setTextColor(0xFF00AA00.toInt())
                    btnConnect.text = "断开"
                }

                override fun onDisconnected() {
                    tvStatus.text = "状态: 未连接"
                    tvStatus.setTextColor(0xFFFF0000.toInt())
                    btnConnect.text = "连接"
                }

                override fun onConnectionError(message: String) {
                    Toast.makeText(
                        this@MainActivity,
                        "连接失败: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                    tvStatus.text = "状态: 未连接"
                    tvStatus.setTextColor(0xFFFF0000.toInt())
                    btnConnect.text = "连接"
                }

                override fun onBytesReceived(data: ByteArray) {
                    // 已在主线程回调
                    this@MainActivity.onBytesReceived(data)
                }
            }
        }

        // 默认显示“接收”页
        showReceivePage()

        // 申请权限 + 刷新设备
        ensureBtPermissionsAndRefresh()
    }

    // ----------------------------------------------------------------
    // 绑定控件
    // ----------------------------------------------------------------
    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        spDevices = findViewById(R.id.spDevices)
        btnRefreshDevices = findViewById(R.id.btnRefreshDevices)
        btnConnect = findViewById(R.id.btnConnect)
        spMode = findViewById(R.id.spMode)

        cbShowTime = findViewById(R.id.cbShowTime)
        cbHex = findViewById(R.id.cbHex)

        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)

        btnTabReceive = findViewById(R.id.btnTabReceive)
        btnTabChart = findViewById(R.id.btnTabChart)

    }

    // ----------------------------------------------------------------
    // 图表初始化
    // ----------------------------------------------------------------
    private fun initChart() {
        chartVoltGlucose.setNoDataText("等待数据...")
        chartVoltGlucose.description = Description().apply {
            text = "电压-电流循环伏安"
        }
        chartVoltGlucose.axisRight.isEnabled = false
        chartVoltGlucose.setTouchEnabled(true)
        chartVoltGlucose.setPinchZoom(true)

        chartVoltGlucose.isHighlightPerTapEnabled = false
        chartVoltGlucose.isHighlightPerDragEnabled = false

        chartVoltGlucose.renderer = SafeLineChartRenderer(
            chartVoltGlucose,
            chartVoltGlucose.animator,
            chartVoltGlucose.viewPortHandler
        )
    }

    // ----------------------------------------------------------------
    // 事件绑定
    // ----------------------------------------------------------------
    private fun initEvents() {
        btnRefreshDevices.setOnClickListener { ensureBtPermissionsAndRefresh() }
        btnConnect.setOnClickListener { toggleConnect() }
        btnClearReceive.setOnClickListener { tvReceive.text = "" }
        btnSaveCsv.setOnClickListener { saveCsv() }

        btnStart.setOnClickListener { sendCommand("START") }
        btnPause.setOnClickListener { sendCommand("PAUSE") }
        btnResume.setOnClickListener { sendCommand("RESUME") }

        btnTabReceive.setOnClickListener { showReceivePage() }
        btnTabChart.setOnClickListener { showChartPage() }

        // [关键修改] 点击“连接”按钮时的逻辑分支
        btnConnect.setOnClickListener {

            // 1. 获取当前设备
            val dev = currentDevice
            if (dev == null) {
                Toast.makeText(this, "请选择蓝牙设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 获取用户选择的模式 (0: 血糖, 1: ECG)
            val selectedMode = spMode.selectedItemPosition

            if (selectedMode == 0) {
                // ==============================
                // 模式 A: 血糖监测 (保持原有逻辑)
                // ==============================
                toggleConnect()

            } else {
                // ==============================
                // 模式 B: ECG/心音 (跳转到新页面)
                // ==============================

                // 为了避免两个页面抢夺蓝牙，如果当前在这里已经连上了，先断开
                if (btManager.isConnected) {
                    btManager.disconnect()
                    Toast.makeText(this, "正在切换到 ECG 模式...", Toast.LENGTH_SHORT).show()
                }

                // 跳转到 HeartMonitorActivity，把设备地址传过去
                val intent = Intent(this, HeartMonitorActivity::class.java)
                intent.putExtra("DEVICE_ADDRESS", dev.address)
                startActivity(intent)
            }
        }
    }

    // [新增] 填充模式选择的内容
    private fun initModeSpinner() {
        // 定义两种模式
        val modes = listOf("血糖监测模式", "ECG/心音模式")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMode.adapter = adapter
    }

    // 发送控制命令给 STM32
    private fun sendCommand(cmd: String) {
        if (!btManager.isConnected) {
            Toast.makeText(this, "尚未连接蓝牙设备", Toast.LENGTH_SHORT).show()
            return
        }

        val line = "$cmd\r\n"
        btManager.sendLine(line)

        tvReceive.append("[APP] Sent: $cmd\n")
        scrollReceive.post { scrollReceive.fullScroll(View.FOCUS_DOWN) }
    }

    // ----------------------------------------------------------------
    // tab 切换
    // ----------------------------------------------------------------
    private fun showReceivePage() {
        receivePanel.visibility = View.VISIBLE
        chartPanel.visibility = View.GONE
        updateTabStyle(isReceiveSelected = true)
    }

    private fun showChartPage() {
        receivePanel.visibility = View.GONE
        chartPanel.visibility = View.VISIBLE
        updateTabStyle(isReceiveSelected = false)

        updateVoltGlucoseChart()
        updateDataTable()
    }

    private fun updateTabStyle(isReceiveSelected: Boolean) {
        val selectedBg = ContextCompat.getColor(this, android.R.color.holo_blue_light)
        val unselectedBg = ContextCompat.getColor(this, android.R.color.darker_gray)
        val selectedText = ContextCompat.getColor(this, android.R.color.white)
        val unselectedText = ContextCompat.getColor(this, android.R.color.black)

        if (isReceiveSelected) {
            btnTabReceive.setBackgroundColor(selectedBg)
            btnTabReceive.setTextColor(selectedText)
            btnTabChart.setBackgroundColor(unselectedBg)
            btnTabChart.setTextColor(unselectedText)
        } else {
            btnTabChart.setBackgroundColor(selectedBg)
            btnTabChart.setTextColor(selectedText)
            btnTabReceive.setBackgroundColor(unselectedBg)
            btnTabReceive.setTextColor(unselectedText)
        }
    }

    // ----------------------------------------------------------------
    // 权限申请
    // ----------------------------------------------------------------
    private fun ensureBtPermissionsAndRefresh() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                needed.toTypedArray(),
                REQ_BT_PERMISSIONS
            )
        } else {
            refreshDevices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_BT_PERMISSIONS) {
            val allGranted =
                grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                refreshDevices()
            } else {
                Toast.makeText(
                    this,
                    "未授予蓝牙/定位权限，无法扫描设备",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ----------------------------------------------------------------
    // 设备扫描（只用已配对经典蓝牙）
    // ----------------------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            Toast.makeText(this, "本机不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        // Android 12+：在真正访问 bondedDevices 前再次确认 BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPermission =
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            if (!hasConnectPermission) {
                Toast.makeText(
                    this,
                    "没有蓝牙权限，无法读取已配对设备，请在系统设置中授权",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val bondedDevices = try {
            btAdapter.bondedDevices
        } catch (e: SecurityException) {
            Toast.makeText(this, "读取已配对设备失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val bonded = bondedDevices?.toList() ?: emptyList()
        if (bonded.isEmpty()) {
            spDevices.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                listOf("无已配对设备")
            )
            currentDevice = null
            return
        }

        val names = bonded.map { "${it.name} (${it.address})" }
        spDevices.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            names
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentDevice = bonded[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                currentDevice = null
            }
        }
        currentDevice = bonded.first()
    }

    // ----------------------------------------------------------------
    // 连接 / 断开
    // ----------------------------------------------------------------
    private fun toggleConnect() {
        if (btManager.isConnected) {
            btManager.disconnect()
        } else {
            val dev = currentDevice
            if (dev == null) {
                Toast.makeText(this, "请选择蓝牙设备", Toast.LENGTH_SHORT).show()
                return
            }

            // Android 12+ 在真正发起连接前再检查一次 BLUETOOTH_CONNECT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasConnectPermission =
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED

                if (!hasConnectPermission) {
                    Toast.makeText(
                        this,
                        "缺少蓝牙连接权限，请在系统设置中授权",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            btManager.connect(dev)
        }
    }

    // ----------------------------------------------------------------
    // 数据接收 + JSON 解析
    // ----------------------------------------------------------------
    private fun currentTimeStr(): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return fmt.format(Date())
    }

    private fun onBytesReceived(data: ByteArray) {
        val textPart: String
        val displayLine: String

        if (cbHex.isChecked) {
            val hex = data.joinToString(" ") { "%02X".format(it) }
            textPart = ""
            displayLine = buildString {
                if (cbShowTime.isChecked) {
                    append("[${currentTimeStr()}] ")
                }
                append(hex)
            }
        } else {
            val txt = String(data, Charsets.UTF_8)
            textPart = txt
            displayLine = buildString {
                if (cbShowTime.isChecked) {
                    append("[${currentTimeStr()}] ")
                }
                append(txt)
            }
        }

        tvReceive.append(displayLine)
        tvReceive.append("\n")
        trimReceiveText()

        scrollReceive.post { scrollReceive.fullScroll(View.FOCUS_DOWN) }

        if (!cbHex.isChecked && textPart.isNotEmpty()) {
            jsonDecoder.feed(textPart) { obj ->
                obj.put("receive_time", currentTimeStr())
                handleJson(obj)
            }
        }
    }

    private fun trimReceiveText(maxLines: Int = 1000) {
        val text = tvReceive.text
        val lines = text.split('\n')
        if (lines.size > maxLines) {
            val sub = lines.takeLast(maxLines).joinToString("\n")
            tvReceive.text = sub
        }
    }

    // ----------------------------------------------------------------
    // JSON -> Monitor -> 图 + 表
    // ----------------------------------------------------------------
    private fun handleJson(obj: JSONObject) {
        monitor.processJson(obj)
        updateVoltGlucoseChart()
        updateDataTable()
    }

    // ----------------------------------------------------------------
    // 循环伏安图
    // ----------------------------------------------------------------
    private fun updateVoltGlucoseChart() {
        val raw = monitor.voltageGlucoseData.toList()
        val data = raw.filter { (v, g) -> v.isFinite() && g.isFinite() }

        if (data.size < 5) {
            chartVoltGlucose.clear()
            chartVoltGlucose.setNoDataText("等待循环伏安数据...")
            chartVoltGlucose.invalidate()
            return
        }

        val segments = buildMonotonicSegments(
            data = data,
            dvThreshold = 1e-4,
            minPoints = 5
        )

        if (segments.isEmpty()) {
            val entries = data.map { (v, g) -> Entry(v.toFloat(), g.toFloat()) }
            val set = LineDataSet(entries, "CV 曲线").apply {
                setDrawCircles(false)
                setDrawValues(false)
                isHighlightEnabled = false
                lineWidth = 1.5f
            }
            chartVoltGlucose.data = LineData(set)
            adjustAxesForVoltGlucose(data)
            chartVoltGlucose.description = Description().apply { text = "电压-电流循环伏安" }
            chartVoltGlucose.invalidate()
            return
        }

        val lineData = LineData()
        val allVolt = mutableListOf<Float>()
        val allCurrent = mutableListOf<Float>()

        val colors = intArrayOf(
            0xFFE53935.toInt(),
            0xFF1E88E5.toInt(),
            0xFF43A047.toInt(),
            0xFFFDD835.toInt(),
            0xFF8E24AA.toInt(),
            0xFF00897B.toInt()
        )

        segments.forEachIndexed { index, seg ->
            if (seg.size < 2) return@forEachIndexed

            val ordered = if (seg.first().first <= seg.last().first) seg else seg.asReversed()

            val entries = ordered.map { (v, g) ->
                val vf = v.toFloat()
                val cf = g.toFloat()
                allVolt.add(vf)
                allCurrent.add(cf)
                Entry(vf, cf)
            }

            val label = if (index % 2 == 0) "正扫 ${index / 2 + 1}" else "回扫 ${index / 2 + 1}"

            val set = LineDataSet(entries, label).apply {
                color = colors[index % colors.size]
                setDrawCircles(false)
                setDrawValues(false)
                isHighlightEnabled = false
                lineWidth = 1.5f
            }
            lineData.addDataSet(set)
        }

        chartVoltGlucose.data = lineData

        if (allVolt.isNotEmpty()) {
            val minV = allVolt.minOrNull() ?: 0f
            val maxV = allVolt.maxOrNull() ?: (minV + 0.1f)
            val dV = (maxV - minV).takeIf { it > 0f } ?: 0.1f

            val xAxis = chartVoltGlucose.xAxis
            xAxis.axisMinimum = minV - 0.05f * dV
            xAxis.axisMaximum = maxV + 0.05f * dV
        }

        if (allCurrent.isNotEmpty()) {
            val minI = allCurrent.minOrNull() ?: 0f
            val maxI = allCurrent.maxOrNull() ?: (minI + 1f)
            val dI = (maxI - minI).takeIf { it > 0f } ?: 1f
            val margin = max(1f, 0.1f * dI)

            val yAxis = chartVoltGlucose.axisLeft
            yAxis.axisMinimum = minI - margin
            yAxis.axisMaximum = maxI + margin
        }

        chartVoltGlucose.axisRight.isEnabled = false
        chartVoltGlucose.description = Description().apply { text = "电压-电流循环伏安" }
        chartVoltGlucose.invalidate()
    }

    private fun adjustAxesForVoltGlucose(data: List<Pair<Double, Double>>) {
        val allVolt = data.map { it.first.toFloat() }
        val allCurrent = data.map { it.second.toFloat() }
        if (allVolt.isEmpty() || allCurrent.isEmpty()) return

        val minV = allVolt.minOrNull() ?: 0f
        val maxV = allVolt.maxOrNull() ?: 1f
        val minI = allCurrent.minOrNull() ?: 0f
        val maxI = allCurrent.maxOrNull() ?: 1f
        val margin = max(1f, (maxI - minI) * 0.1f)

        chartVoltGlucose.xAxis.axisMinimum = minV - 0.1f
        chartVoltGlucose.xAxis.axisMaximum = maxV + 0.1f
        chartVoltGlucose.axisLeft.axisMinimum = minI - margin
        chartVoltGlucose.axisLeft.axisMaximum = maxI + margin
    }

    private fun buildMonotonicSegments(
        data: List<Pair<Double, Double>>,
        dvThreshold: Double = 1e-4,
        minPoints: Int = 5
    ): List<List<Pair<Double, Double>>> {
        if (data.size < 2) return emptyList()

        val segments = mutableListOf<MutableList<Pair<Double, Double>>>()
        var currentSegment = mutableListOf<Pair<Double, Double>>()
        currentSegment.add(data[0])

        var currentDir = 0 // 0=未知, 1=升压, -1=降压

        for (i in 1 until data.size) {
            val prev = data[i - 1]
            val cur = data[i]
            val dv = cur.first - prev.first

            val dir = when {
                kotlin.math.abs(dv) < dvThreshold -> currentDir
                dv > 0 -> 1
                else -> -1
            }

            if (currentDir == 0 || dir == currentDir) {
                currentSegment.add(cur)
                if (currentDir == 0) currentDir = dir
            } else {
                if (currentSegment.size >= minPoints) {
                    segments.add(currentSegment)
                }
                currentSegment = mutableListOf(prev, cur)
                currentDir = dir
            }
        }

        if (currentSegment.size >= minPoints) {
            segments.add(currentSegment)
        }

        return segments
    }

    // ----------------------------------------------------------------
    // 数据表格
    // ----------------------------------------------------------------
    private fun updateDataTable(maxRows: Int = 20) {
        val records = monitor.records
        if (records.isEmpty()) {
            tableData.removeAllViews()
            return
        }

        tableData.removeAllViews()

        val header = TableRow(this)
        header.addView(makeCell("时间(s)", bold = true))
        header.addView(makeCell("电压(V)", bold = true))
        header.addView(makeCell("电流(mA)", bold = true))
        tableData.addView(header)

        val slice = if (records.size > maxRows) {
            records.takeLast(maxRows)
        } else {
            records
        }

        for (r in slice) {
            val row = TableRow(this)
            row.addView(makeCell(String.format(Locale.US, "%.2f", r.seconds)))
            row.addView(makeCell(String.format(Locale.US, "%.3f", r.voltage)))
            row.addView(makeCell(String.format(Locale.US, "%.3f", r.glucoseCurrent)))
            tableData.addView(row)
        }
    }

    private fun makeCell(text: String, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(8, 4, 8, 4)
            textSize = 12f
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }
    }

    // ----------------------------------------------------------------
    // CSV 保存
    // ----------------------------------------------------------------
    private fun saveCsv() {
        val records = monitor.records
        if (records.isEmpty()) {
            Toast.makeText(this, "暂无可保存的数据", Toast.LENGTH_SHORT).show()
            return
        }

        val dir = getExternalFilesDir(null) ?: filesDir
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val name = "serial_data_${fmt.format(Date())}.csv"
        val file = File(dir, name)

        try {
            file.printWriter().use { out ->
                out.println("时间(秒),电流(mA),电压(V),接收时间")
                records.forEach {
                    out.println(
                        "${it.seconds}," +
                                "${it.glucoseCurrent}," +
                                "${it.voltage}," +
                                it.receiveTime
                    )
                }
            }
            Toast.makeText(
                this,
                "已保存到: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "保存失败: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        btManager.disconnect()
    }
}