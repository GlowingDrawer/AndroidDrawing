package com.example.ECGDemo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ECGDemo.bt.BluetoothSerialManager
import com.example.ECGDemo.chart.ECGSurfaceView
import com.example.ECGDemo.data.EcgProtocolParser

class HeartMonitorActivity : AppCompatActivity() {

    private lateinit var ecgView: ECGSurfaceView
    private lateinit var tvTitle: TextView // [新增] 顶部大标题
    private lateinit var tvInfo: TextView
    private lateinit var btnExit: Button

    private lateinit var btManager: BluetoothSerialManager
    private lateinit var parser: EcgProtocolParser

    private var deviceAddress: String? = null

    // 标记是否为直接接收模式 (true=心音, false=心电)
    private var isRawMode = false

    private val connectHandler = Handler(Looper.getMainLooper())
    private val connectRunnable = Runnable { connectBluetooth() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 获取 Intent 参数
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        isRawMode = intent.getBooleanExtra("IS_RAW_MODE", false)

        if (deviceAddress.isNullOrEmpty()) {
            Toast.makeText(this, "错误：未传入设备地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // --- 布局初始化 ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt()) // 黑色背景
        }

        // [新增] 顶部大标题
        tvTitle = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 32, 0, 8)
            // 初始文字，稍后会根据模式修改
            text = "监测模式"
        }
        rootLayout.addView(tvTitle)

        // 状态栏 (连接状态 + 退出按钮)
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        tvInfo = TextView(this).apply {
            text = "正在初始化..."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnExit = Button(this).apply {
            text = "退出"
            textSize = 14f
            setOnClickListener { finish() }
        }

        headerLayout.addView(tvInfo)
        headerLayout.addView(btnExit)
        rootLayout.addView(headerLayout)

        // 波形视图
        ecgView = ECGSurfaceView(this)
        rootLayout.addView(ecgView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(rootLayout)

        // --- 2. 根据模式设置标题和量程 ---
        if (isRawMode) {
            // === 心音模式 (直接接收) ===
            tvTitle.text = "心音监测 (Heart Sound)"
            tvTitle.setTextColor(Color.CYAN) // 用青色区分心音
            ecgView.setSpan(100000f) // 量程 ±50000
            tvInfo.text = "模式: 直接接收 | 准备连接..."
        } else {
            // === 心电模式 (协议解析) ===
            tvTitle.text = "心电监测 (ECG)"
            tvTitle.setTextColor(Color.GREEN) // 用绿色区分心电
            ecgView.setSpan(40000f)  // 量程 ±20000
            tvInfo.text = "模式: 协议解析 | 准备连接..."
        }

        // 初始化解析器
        parser = EcgProtocolParser { value ->
            ecgView.addSample(value)
        }

        // 延时连接
        connectHandler.postDelayed(connectRunnable, 1000)
    }

    private fun connectBluetooth() {
        if (isFinishing || isDestroyed) return

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            tvInfo.text = "错误：无效的地址"
            return
        }

        tvInfo.text = "正在连接: ${device.name}..."

        btManager = BluetoothSerialManager(adapter, lifecycleScope).apply {
            listener = object : BluetoothSerialManager.Listener {
                override fun onConnected(d: BluetoothDevice) {
                    runOnUiThread {
                        tvInfo.text = "已连接: ${d.name}"
                        tvInfo.setTextColor(Color.GREEN)
                    }
                }

                override fun onDisconnected() {
                    runOnUiThread {
                        tvInfo.text = "连接已断开"
                        tvInfo.setTextColor(Color.RED)
                    }
                }

                override fun onConnectionError(message: String) {
                    runOnUiThread {
                        tvInfo.text = "连接错误: $message"
                    }
                }

                override fun onBytesReceived(data: ByteArray) {
                    if (isRawMode) {
                        // === 心音模式：直接解析文本数值 ===
                        try {
                            val text = String(data).trim()
                            if (text.isNotEmpty()) {
                                val parts = text.split(Regex("\\s+"))
                                for (part in parts) {
                                    val value = part.toFloatOrNull()
                                    if (value != null) {
                                        ecgView.addSample(value)
                                    }
                                }
                            }
                        } catch (e: Exception) { }
                    } else {
                        // === 心电模式：协议解包 ===
                        parser.pushData(data)
                    }
                }
            }
        }
        btManager.connect(device)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectHandler.removeCallbacks(connectRunnable)
        if (::btManager.isInitialized) {
            btManager.disconnect()
        }
    }
}