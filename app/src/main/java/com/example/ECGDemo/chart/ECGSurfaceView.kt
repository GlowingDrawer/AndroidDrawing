package com.example.ECGDemo.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 专用于绘制高频心电/心音波形的 SurfaceView
 * 固定量程版：Y轴范围固定为 -20000 ~ +20000
 */
class ECGSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var renderThread: Thread? = null
    @Volatile private var isRunning = false
    private val holderRef = holder

    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 1f
    }

    private val zeroLinePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.LTGRAY
        textSize = 24f
        isAntiAlias = true
    }

    // 缓冲区大小
    private val bufferSize = 3000
    private val dataBuffer = FloatArray(bufferSize)
    private var writeIndex = 0

    private val incomingQueue = ConcurrentLinkedQueue<Float>()

    // 设定固定跨度
    // 范围 -20000 到 +20000，总跨度为 40000
    private val fixedSpan = 40000f

    init {
        holderRef.addCallback(this)
        setZOrderOnTop(true)
        holderRef.setFormat(PixelFormat.TRANSPARENT)
    }

    fun addSample(value: Float) {
        incomingQueue.offer(value)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        renderThread = Thread(this).apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        try {
            renderThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun run() {
        while (isRunning) {
            if (!holderRef.surface.isValid) continue

            while (!incomingQueue.isEmpty()) {
                val value = incomingQueue.poll() ?: break
                dataBuffer[writeIndex] = value
                writeIndex = (writeIndex + 1) % bufferSize
            }

            val canvas = holderRef.lockCanvas()
            if (canvas != null) {
                try {
                    drawFrame(canvas)
                } finally {
                    holderRef.unlockCanvasAndPost(canvas)
                }
            }

            try { Thread.sleep(16) } catch (e: Exception) {}
        }
    }

    private fun drawFrame(canvas: Canvas) {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // 绘制零线
        canvas.drawLine(0f, centerY, width, centerY, zeroLinePaint)

        // 绘制辅助参考线 (可选)
        // 例如在 +10000 和 -10000 处画淡灰色的线
        // 计算缩放比例：保留一点点边距 (0.95)，让 20000 稍微离边缘有一点距离
        // 如果想让 20000 绝对顶格，把 0.95f 改成 1.0f
        val scaleY = (height * 0.95f) / fixedSpan

        // 辅助线位置计算
        val yTop = centerY - (10000f * scaleY)
        val yBottom = centerY - (-10000f * scaleY)
        canvas.drawLine(0f, yTop, width, yTop, gridPaint)
        canvas.drawLine(0f, yBottom, width, yBottom, gridPaint)

        // -----------------------------------------------------------
        // 绘制波形 (固定比例)
        // -----------------------------------------------------------
        val stepX = width / bufferSize
        val startPos = writeIndex

        for (i in 0 until bufferSize - 1) {
            val idx1 = (startPos + i) % bufferSize
            val idx2 = (startPos + i + 1) % bufferSize

            val x1 = i * stepX
            val x2 = (i + 1) * stepX

            // Y坐标公式：屏幕中心 - (数值 * 固定缩放比例)
            val y1 = centerY - (dataBuffer[idx1] * scaleY)
            val y2 = centerY - (dataBuffer[idx2] * scaleY)

            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        // 显示当前量程提示 (左上角)
        canvas.drawText("Range: ±20000", 20f, 40f, textPaint)
    }
}