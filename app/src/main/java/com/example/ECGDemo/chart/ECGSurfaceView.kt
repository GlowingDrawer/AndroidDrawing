package com.example.ECGDemo.chart  // <--- 包名已修改

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

    // 缓冲区大小
    private val bufferSize = 2000
    private val dataBuffer = FloatArray(bufferSize)
    private var writeIndex = 0

    // 接收队列
    private val incomingQueue = ConcurrentLinkedQueue<Float>()

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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

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
        // 1. 清屏
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // 2. 绘制中心参考线
        canvas.drawLine(0f, centerY, width, centerY, gridPaint)

        // -----------------------------------------------------------
        // 针对 -50000 ~ +50000 的滚动显示适配
        // -----------------------------------------------------------

        val dataSpan = 100000f // 数据跨度
        val scaleY = (height * 0.9f) / dataSpan // 缩放比例
        val baseValue = 0f // 基准值

        val stepX = width / bufferSize

        // --- 核心修改：滚动逻辑 ---
        // writeIndex 当前指向的是“最老”的数据（即下一个要被覆盖的位置）
        // 所以我们从 writeIndex 开始读取，就是按时间顺序从 旧 -> 新 读取

        // startPos: 缓冲区中“最老”数据的索引
        val startPos = writeIndex

        // 我们需要绘制 bufferSize - 1 段线条
        for (i in 0 until bufferSize - 1) {

            // 计算当前点在循环缓冲区中的真实索引
            val idx1 = (startPos + i) % bufferSize
            val idx2 = (startPos + i + 1) % bufferSize

            // 计算屏幕上的 X 坐标
            // i=0 时 x=0 (屏幕最左边，显示最老数据)
            // i=max 时 x=width (屏幕最右边，显示最新数据)
            val x1 = i * stepX
            val x2 = (i + 1) * stepX

            // 计算 Y 坐标 (保持之前的算法)
            val y1 = centerY - ((dataBuffer[idx1] - baseValue) * scaleY)
            val y2 = centerY - ((dataBuffer[idx2] - baseValue) * scaleY)

            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        // 滚动模式下通常不需要红色的扫描线，因为最右边就是最新的
        // 如果你喜欢，可以在最右边画一个点或者小箭头提示最新位置
    }
}