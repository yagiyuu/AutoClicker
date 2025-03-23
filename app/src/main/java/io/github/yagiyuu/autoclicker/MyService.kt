package io.github.yagiyuu.autoclicker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import com.topjohnwu.superuser.ipc.RootService
import io.github.yagiyuu.autoclicker.util.Compat.TYPE_OVERLAY
import io.github.yagiyuu.autoclicker.util.Utils.disableAnimation
import io.github.yagiyuu.autoclicker.view.ClickPointView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class MyService : Service() {
    companion object {
        const val CHANNEL_ID = "AutoClickerChannel"
    }

    private lateinit var windowManager: WindowManager

    private lateinit var floatingView: View
    private lateinit var clickPointView: ClickPointView

    private val menuParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            TYPE_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).disableAnimation()
    }

    private val clickPointParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            TYPE_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).disableAnimation()
    }

    private var isAutoClicking: Boolean = false
    private var touchService: ITouchService? = null
    private var clickJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder
        ) {
            touchService = ITouchService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            touchService = null
            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showNotification()
        initFloatingViews()
        bindToTouchService()
    }

    private fun getClickPointLocation(): Pair<Int, Int> {
        val location = IntArray(2)
        with(clickPointView) {
            getLocationOnScreen(location)
            return Pair(location[0] + width, location[1] + height)
        }
    }

    private fun startAutoClicking() {
        disableClickPointInteraction()
        clickJob = CoroutineScope(Dispatchers.Default).launch {
            val (x, y) = getClickPointLocation()
            val (fx, fy) = Pair(x.toFloat(), y.toFloat())
            while (isAutoClicking && isActive) {
                touchService?.touch(fx, fy)
            }
        }
    }

    private fun stopAutoClicking() {
        clickJob?.cancel()
        clickJob = null
        enableClickPointInteraction()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingViews() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_layout, null as? ViewGroup?)
        val swAutoClick = floatingView.findViewById<ImageView>(R.id.swAutoClick)
        val btnExit = floatingView.findViewById<ImageView>(R.id.btnExit)
        val dragHandle = floatingView.findViewById<ImageView>(R.id.dragHandle)

        swAutoClick.setOnClickListener {
            isAutoClicking = !isAutoClicking
            if (isAutoClicking) {
                startAutoClicking()
                swAutoClick.setImageResource(R.drawable.stop)
            } else {
                stopAutoClicking()
                swAutoClick.setImageResource(R.drawable.play)
            }
        }

        swAutoClick.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY

                        if (isAutoClicking) {
                            v.performClick()
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        if (abs(dx) < 10 && abs(dy) < 10) {
                            v.performClick()
                        }
                    }
                }
                return true
            }
        })

        btnExit.setOnClickListener {
            stopSelf()
        }

        setDraggableView(floatingView, dragHandle, menuParams)
        windowManager.addView(floatingView, menuParams)

        clickPointView = ClickPointView(this)
        setDraggableView(clickPointView, clickPointView, clickPointParams)
        windowManager.addView(clickPointView, clickPointParams)
    }

    private fun enableClickPointInteraction() {
        clickPointParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(clickPointView, clickPointParams)
    }

    private fun disableClickPointInteraction() {
        clickPointParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(clickPointView, clickPointParams)
    }

    private fun setDraggableView(container: View, view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(container, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun bindToTouchService() {
        val intent = Intent(this, TouchService::class.java)
        RootService.bind(intent, connection)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoClicking()
        windowManager.removeView(floatingView)
        windowManager.removeView(clickPointView)
        RootService.unbind(connection)
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_running))
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoClicker Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}