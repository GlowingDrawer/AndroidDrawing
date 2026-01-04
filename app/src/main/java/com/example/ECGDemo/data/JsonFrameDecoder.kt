package com.example.ECGDemo.data

import org.json.JSONObject

/**
 * 串口文本 -> JSON 对象
 * 解决：
 *  - 一帧里有多个 {...}{...}
 *  - 一帧只收到一半 {....  下一帧补齐
 */
class JsonFrameDecoder {

    private val buffer = StringBuilder()

    /**
     * 追加一段文本，尽可能解析出完整 JSON。
     * 每解析出一个 JSONObject，就回调一次 onJson。
     */
    fun feed(chunk: String, onJson: (JSONObject) -> Unit) {
        if (chunk.isEmpty()) return

        buffer.append(chunk)

        while (true) {
            val start = buffer.indexOf("{")
            if (start == -1) {
                // 没有起始符，整段作废
                buffer.clear()
                return
            }

            val end = buffer.indexOf("}", start)
            if (end == -1) {
                // 有 { 没有 }，保留从 { 开始的部分，等待下一帧
                if (start > 0) {
                    buffer.delete(0, start)
                }
                return
            }

            val jsonStr = buffer.substring(start, end + 1)
            buffer.delete(0, end + 1)

            try {
                val obj = JSONObject(jsonStr)
                onJson(obj)
            } catch (_: Exception) {
                // 这一段不是合法 JSON，继续寻找下一个 { ... }
                continue
            }
        }
    }

    fun reset() {
        buffer.clear()
    }
}
