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
import com.example.ECGDemo.data.EcgProtocolParser // [新增 import]

class HeartMonitorActivity : AppCompatActivity() {

    private lateinit var ecgView: ECGSurfaceView
    private lateinit var tvInfo: TextView
    private lateinit var btnExit: Button

    private lateinit var btManager: BluetoothSerialManager

    // [新增] 解析器实例
    private lateinit var parser: EcgProtocolParser

    private var deviceAddress: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 布局初始化 ---
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
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

        // 延时连接，防止蓝牙占线
        tvInfo.text = "正在准备连接..."
        Handler(Looper.getMainLooper()).postDelayed({
            connectBluetooth()
        }, 1000)
    }

    private fun connectBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(deviceAddress)

        tvInfo.text = "正在连接: ${device.name}..."

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
                    // [修改] 将原始数据直接塞给解析器
                    parser.pushData(data)
                }
            }
        }
        btManager.connect(device)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::btManager.isInitialized) {
            btManager.disconnect()
        }
    }
}