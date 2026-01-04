package com.example.ECGDemo.data

import java.util.LinkedList

/**
 * 专用于解析 ECG 协议的类
 * 协议格式: AA AA 04 80 02 HH LL
 */
class EcgProtocolParser(private val onSampleReceived: (Float) -> Unit) {

    // 使用 List 作为缓冲区，模拟 Python 的 extend 和 slicing
    // 虽然不是性能最高的写法，但是逻辑最接近你提供的 Python 代码，且足够处理蓝牙流
    private val buffer = LinkedList<Byte>()

    /**
     * 接收原始蓝牙字节流
     */
    fun pushData(data: ByteArray) {
        for (b in data) {
            buffer.add(b)
        }
        parse()
    }

    private fun parse() {
        // 类似 Python: while i <= len - 7
        while (buffer.size >= 7) {
            // 1. 检查帧头 AA AA
            // Kotlin 的 Byte 是有符号的，0xAA 也就是 -86，所以要用 toInt() & 0xFF 转无符号判断
            val b0 = buffer[0].toInt() and 0xFF
            val b1 = buffer[1].toInt() and 0xFF

            if (b0 != 0xAA) {
                buffer.removeFirst() // i += 1
                continue
            }
            if (b1 != 0xAA) {
                // 如果第一个是 AA 但第二个不是，移除第一个继续找
                buffer.removeFirst()
                continue
            }

            // 此时 buffer[0] 和 buffer[1] 都是 AA
            // 协议: AA AA [Type=04] [Len=80] [Cmd=02] [HH] [LL]
            val packetType = buffer[2].toInt() and 0xFF

            if (packetType == 0x04) {
                val lenField = buffer[3].toInt() and 0xFF
                val cmdField = buffer[4].toInt() and 0xFF

                if (lenField == 0x80 && cmdField == 0x02) {
                    val high = buffer[5].toInt() and 0xFF
                    val low = buffer[6].toInt() and 0xFF

                    // 合并高低字节
                    var rawValue = (high shl 8) or low

                    // 处理符号位 (Python代码逻辑: if raw_value >= 32768: raw_value -= 65536)
                    // 在 Kotlin/Java 中，short 类型本身就是 16位有符号的 (-32768 ~ 32767)
                    // 所以直接强转为 Short 即可达到同样效果
                    val signedValue = rawValue.toShort()

                    // 回调给界面
                    onSampleReceived(signedValue.toFloat())

                    // 移除已处理的 7 个字节 (i += 7)
                    repeat(7) { buffer.removeFirst() }
                    continue
                }
            }

            // 如果不是目标包，移除第一个字节继续向后搜索 (i += 1)
            buffer.removeFirst()
        }
    }
}