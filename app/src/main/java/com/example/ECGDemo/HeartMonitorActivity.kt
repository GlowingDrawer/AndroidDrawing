package com.example.ECGDemo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // [新增] 是否为直接接收模式
    private var isRawMode = false

    private lateinit var ecgView: ECGSurfaceView
    private lateinit var tvInfo: TextView
    private lateinit var btnExit: Button

    private lateinit var btManager: BluetoothSerialManager

    // 解析器实例
    private lateinit var parser: EcgProtocolParser

    private var deviceAddress: String? = null

    // [修复] 将 Handler 和 Runnable 提升为成员变量，以便在 onDestroy 中移除
    private val connectHandler = Handler(Looper.getMainLooper())
    private val connectRunnable = Runnable { connectBluetooth() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [新增] 获取 MainActivity 传过来的模式参数
        isRawMode = intent.getBooleanExtra("IS_RAW_MODE", false)

        // --- 布局初始化 ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt()) // 黑色背景
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        tvInfo = TextView(this).apply {
            text = "正在初始化..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnExit = Button(this).apply {
            text = "断开并退出"
            setOnClickListener { finish() }
        }

        headerLayout.addView(tvInfo)
        headerLayout.addView(btnExit)

        ecgView = ECGSurfaceView(this)

        rootLayout.addView(headerLayout)
        rootLayout.addView(ecgView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(rootLayout)

        // --- 初始化解析器 ---
        parser = EcgProtocolParser { value ->
            // 解析器回调这里，value 是已经处理好的实际数值
            ecgView.addSample(value)
        }

        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        if (deviceAddress.isNullOrEmpty()) {
            Toast.makeText(this, "错误：未传入设备地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // [建议] 更新一下提示文字，让用户知道当前是什么模式
        tvInfo.text = "模式: ${if (isRawMode) "直接接收" else "协议解析"} | 正在初始化..."

        // 延时连接，防止蓝牙占线
        tvInfo.text = "正在准备连接..."
        // [修复] 使用成员变量 handler 发送延时任务
        connectHandler.postDelayed(connectRunnable, 1000)
    }

    private fun connectBluetooth() {
        // 再次检查 Activity 是否已经被销毁（双重保险）
        if (isFinishing || isDestroyed) return

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            tvInfo.text = "错误：设备不支持蓝牙"
            return
        }

        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            tvInfo.text = "错误：无效的蓝牙地址"
            return
        }

        tvInfo.text = "正在连接: ${device.name ?: "未知设备"}..."

        btManager = BluetoothSerialManager(adapter, lifecycleScope).apply {
            listener = object : BluetoothSerialManager.Listener {
                override fun onConnected(d: BluetoothDevice) {
                    runOnUiThread {
                        tvInfo.text = "已连接: ${d.name} | 模式: ECG/心音"
                        tvInfo.setTextColor(0xFF00FF00.toInt())
                    }
                }

                override fun onDisconnected() {
                    runOnUiThread {
                        tvInfo.text = "连接已断开"
                        tvInfo.setTextColor(0xFFFF0000.toInt())
                    }
                }

                override fun onConnectionError(message: String) {
                    runOnUiThread {
                        tvInfo.text = "连接错误: $message"
                    }
                }

                override fun onBytesReceived(data: ByteArray) {
                    if (isRawMode) {
                        // === 模式 2: 直接接收 (Raw Text) ===
                        // 假设设备发送的是 "123\n" 或者 "123 124 125" 这种纯文本数字
                        try {
                            val text = String(data).trim()
                            if (text.isNotEmpty()) {
                                // 可能一次收到多个数字，按空白字符分割
                                val parts = text.split(Regex("\\s+"))
                                for (part in parts) {
                                    // 尝试转成 Float 并绘图
                                    val value = part.toFloatOrNull()
                                    if (value != null) {
                                        ecgView.addSample(value)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    } else {
                        // === 模式 1: 协议解析 (AA AA ...) ===
                        parser.pushData(data)
                    }
                }
            }
        }
        btManager.connect(device)
    }

    override fun onDestroy() {
        super.onDestroy()
        // [修复] 移除所有未执行的延时任务，防止内存泄漏或 Crash
        connectHandler.removeCallbacks(connectRunnable)

        if (::btManager.isInitialized) {
            btManager.disconnect()
        }
    }
}