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
 * 支持动态设置量程
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

    // [修改] 默认跨度 40000 (-20000 ~ +20000)
    // 现在它是 var，可以被外部修改
    private var currentSpan = 40000f

    init {
        holderRef.addCallback(this)
        setZOrderOnTop(true)
        holderRef.setFormat(PixelFormat.TRANSPARENT)
    }

    /**
     * [新增] 设置显示范围跨度
     * 例如：span=40000f 表示 ±20000
     * span=100000f 表示 ±50000
     */
    fun setSpan(span: Float) {
        this.currentSpan = span
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

        // [修改] 使用 currentSpan 计算缩放比例
        // 保留一点边距 (0.95f)
        val scaleY = (height * 0.95f) / currentSpan

        // 绘制辅助线 (可选，这里以 ±1/4 量程为例)
        val quarterSpan = currentSpan / 4f
        val yTop = centerY - (quarterSpan * scaleY)
        val yBottom = centerY - (-quarterSpan * scaleY)
        canvas.drawLine(0f, yTop, width, yTop, gridPaint)
        canvas.drawLine(0f, yBottom, width, yBottom, gridPaint)

        val stepX = width / bufferSize
        val startPos = writeIndex

        for (i in 0 until bufferSize - 1) {
            val idx1 = (startPos + i) % bufferSize
            val idx2 = (startPos + i + 1) % bufferSize

            val x1 = i * stepX
            val x2 = (i + 1) * stepX

            val y1 = centerY - (dataBuffer[idx1] * scaleY)
            val y2 = centerY - (dataBuffer[idx2] * scaleY)

            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        // 显示当前量程
        val rangeVal = (currentSpan / 2).toInt()
        canvas.drawText("Range: ±$rangeVal", 20f, 40f, textPaint)
    }
}