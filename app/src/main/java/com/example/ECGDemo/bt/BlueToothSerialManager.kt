package com.example.ECGDemo.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID

/**
 * 负责：
 *  - 建立 / 断开 SPP 蓝牙连接
 *  - 后台循环读取字节流
 *  - 发送字符串命令
 * 不关心：
 *  - UI
 *  - JSON 解析
 *  - 业务逻辑（电压、电流等）
 */
class BluetoothSerialManager(
    private val adapter: BluetoothAdapter?,
    private val scope: CoroutineScope
) {

    interface Listener {
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected()
        fun onConnectionError(message: String)
        fun onBytesReceived(data: ByteArray)
    }

    var listener: Listener? = null

    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (adapter == null) {
            listener?.onConnectionError("本机不支持蓝牙")
            return
        }
        if (isConnected) {
            disconnect()
        }

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        scope.launch(Dispatchers.IO) {
            try {
                try {
                    adapter.cancelDiscovery()
                } catch (_: SecurityException) {
                    // 没权限取消扫描也没关系
                }

                val tmp = device.createRfcommSocketToServiceRecord(uuid)
                tmp.connect()
                socket = tmp

                withContext(Dispatchers.Main) {
                    listener?.onConnected(device)
                }

                startReadLoop(tmp)
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    listener?.onConnectionError("蓝牙权限不足: ${e.message}")
                }
                closeSocket()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener?.onConnectionError(e.message ?: "未知错误")
                }
                closeSocket()
            }
        }
    }


    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                readJob?.cancel()
                readJob = null
            } catch (_: Exception) {
            }
            closeSocket()
            withContext(Dispatchers.Main) {
                listener?.onDisconnected()
            }
        }
    }

    fun sendLine(line: String) {
        val s = socket ?: return
        scope.launch(Dispatchers.IO) {
            try {
                s.outputStream.write(line.toByteArray(Charsets.UTF_8))
                s.outputStream.flush()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    listener?.onConnectionError("发送失败: ${e.message}")
                }
            }
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        } finally {
            socket = null
        }
    }

    private fun startReadLoop(s: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val input = s.inputStream
            val buf = ByteArray(1024)
            try {
                while (isActive) {
                    val n = input.read(buf)
                    if (n <= 0) continue
                    val data = buf.copyOf(n)
                    withContext(Dispatchers.Main) {
                        listener?.onBytesReceived(data)
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    listener?.onConnectionError("连接中断: ${e.message}")
                    listener?.onDisconnected()
                }
                closeSocket()
            }
        }
    }
}
